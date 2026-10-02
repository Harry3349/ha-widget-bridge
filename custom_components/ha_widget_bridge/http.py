"""JSON-API für die Android-App.

Alle Endpunkte liegen unter ``/api/ha_widget_bridge/`` und verlangen einen
normalen HA-Zugriffstoken (``Authorization: Bearer <token>``), genau wie die
übrigen HA-REST-Endpunkte.

* ``GET    /api/ha_widget_bridge/widgets``                        – alle Definitionen
* ``POST   /api/ha_widget_bridge/widgets``                        – anlegen/ändern
* ``GET    /api/ha_widget_bridge/widgets/{id}``                   – eine Definition
* ``DELETE /api/ha_widget_bridge/widgets/{id}``                   – löschen
* ``GET    /api/ha_widget_bridge/widgets/{id}/snapshot``          – gerenderter Inhalt + Button-Zustände
* ``POST   /api/ha_widget_bridge/action``                         – Button drücken / Service aufrufen
"""

from __future__ import annotations

import re
from http import HTTPStatus
from typing import Any

from aiohttp import web
from homeassistant.components.http import HomeAssistantView
from homeassistant.core import HomeAssistant

from .actions import async_run_button
from .const import (
    DOMAIN,
    LOGGER,
    URL_ACTION,
    URL_PREVIEW,
    URL_SNAPSHOT,
    URL_WIDGET,
    URL_WIDGETS,
)
from .render import async_render_widget, button_view, row_view, value_view
from .store import WidgetValidationError, get_store, normalize_widget

_SERVICE_RE = re.compile(r"^[a-z0-9_]+\.[a-z0-9_]+$")


def _hass(request: web.Request) -> HomeAssistant:
    return request.app["hass"]


def _not_configured() -> web.Response:
    return web.json_response(
        {
            "error": "HA Widget Bridge ist nicht eingerichtet. Bitte die Integration in "
            "Einstellungen > Geräte & Dienste hinzufügen."
        },
        status=HTTPStatus.SERVICE_UNAVAILABLE,
    )


async def _json_body(request: web.Request) -> dict[str, Any] | None:
    try:
        payload = await request.json()
    except ValueError:
        return None
    return payload if isinstance(payload, dict) else None


def _snapshot_payload(hass: HomeAssistant, widget: dict[str, Any], rendered: dict[str, Any]) -> dict[str, Any]:
    """Alles, was die App zum Zeichnen eines Widgets braucht."""
    return {
        "id": widget["id"],
        "name": widget["name"],
        "revision": widget["revision"],
        "updated_at": widget["updated_at"],
        "text_size": widget["text_size"],
        "theme": widget["theme"],
        "html": rendered["html"],
        "text": rendered["text"],
        "error": rendered["error"],
        "template_used": rendered["template_used"],
        "value_columns": widget["value_columns"],
        "value_label_above": widget["value_label_above"],
        "values": value_view(hass, widget),
        "rows": rendered.get("rows") or row_view(hass, widget),
        "watch_rows": widget.get("watch_rows", 0),
        "watch_scale": widget.get("watch_scale", 1.0),
        "buttons": button_view(hass, widget),
    }


class WidgetsView(HomeAssistantView):
    """Liste aller Widgets – und Anlegen/Ändern eines Widgets."""

    url = URL_WIDGETS
    name = f"api:{DOMAIN}:widgets"
    requires_auth = True

    async def get(self, request: web.Request) -> web.Response:
        store = get_store(_hass(request))
        if store is None:
            return _not_configured()
        return web.json_response({"widgets": store.as_list()})

    async def post(self, request: web.Request) -> web.Response:
        hass = _hass(request)
        store = get_store(hass)
        if store is None:
            return _not_configured()

        payload = await _json_body(request)
        if payload is None:
            return web.json_response({"error": "Ungültiger JSON-Body"}, status=HTTPStatus.BAD_REQUEST)

        try:
            widget = await store.async_upsert(payload)
        except WidgetValidationError as err:
            return web.json_response({"error": str(err)}, status=HTTPStatus.BAD_REQUEST)

        return web.json_response({"widget": widget}, status=HTTPStatus.CREATED)


class WidgetView(HomeAssistantView):
    """Einzelnes Widget lesen oder löschen."""

    url = URL_WIDGET
    name = f"api:{DOMAIN}:widget"
    requires_auth = True

    async def get(self, request: web.Request, widget_id: str) -> web.Response:
        hass = _hass(request)
        store = get_store(hass)
        if store is None:
            return _not_configured()

        widget = store.get(widget_id)
        if widget is None:
            return web.json_response({"error": "Unbekanntes Widget"}, status=HTTPStatus.NOT_FOUND)

        rendered = await async_render_widget(hass, widget)
        return web.json_response(
            {"widget": widget, "html": rendered["html"], "error": rendered["error"]}
        )

    async def delete(self, request: web.Request, widget_id: str) -> web.Response:
        store = get_store(_hass(request))
        if store is None:
            return _not_configured()

        if not await store.async_delete(widget_id):
            return web.json_response({"error": "Unbekanntes Widget"}, status=HTTPStatus.NOT_FOUND)

        return web.json_response({"ok": True})


class SnapshotView(HomeAssistantView):
    """Alles, was die App zum Zeichnen des Widgets braucht – in einem Request."""

    url = URL_SNAPSHOT
    name = f"api:{DOMAIN}:snapshot"
    requires_auth = True

    async def get(self, request: web.Request, widget_id: str) -> web.Response:
        hass = _hass(request)
        store = get_store(hass)
        if store is None:
            return _not_configured()

        widget = store.get(widget_id)
        if widget is None:
            return web.json_response({"error": "Unbekanntes Widget"}, status=HTTPStatus.NOT_FOUND)

        rendered = await async_render_widget(hass, widget)

        return web.json_response(_snapshot_payload(hass, widget, rendered))


class PreviewView(HomeAssistantView):
    """Eine Widget-Definition rendern, ohne sie zu speichern (Editor-Vorschau)."""

    url = URL_PREVIEW
    name = f"api:{DOMAIN}:preview"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        hass = _hass(request)

        payload = await _json_body(request)
        if payload is None:
            return web.json_response(
                {"error": "Ungültiger JSON-Body"}, status=HTTPStatus.BAD_REQUEST
            )

        data = dict(payload)
        data.setdefault("id", "preview")
        data.setdefault("name", "Vorschau")

        try:
            widget = normalize_widget(hass, data)
        except WidgetValidationError as err:
            return web.json_response({"error": str(err)}, status=HTTPStatus.BAD_REQUEST)

        widget["revision"] = 0
        widget["updated_at"] = None

        rendered = await async_render_widget(hass, widget)
        return web.json_response(_snapshot_payload(hass, widget, rendered))


class ActionView(HomeAssistantView):
    """Button eines Widgets drücken oder direkt einen Service aufrufen."""

    url = URL_ACTION
    name = f"api:{DOMAIN}:action"
    requires_auth = True

    async def post(self, request: web.Request) -> web.Response:
        hass = _hass(request)
        store = get_store(hass)
        if store is None:
            return _not_configured()

        payload = await _json_body(request)
        if payload is None:
            return web.json_response({"error": "Ungültiger JSON-Body"}, status=HTTPStatus.BAD_REQUEST)

        widget_id = payload.get("widget_id")
        button_key = payload.get("button") or payload.get("key")

        # --- Variante 1: Button eines konfigurierten Widgets ---------------
        if widget_id and button_key:
            widget = store.get(str(widget_id))
            if widget is None:
                return web.json_response(
                    {"error": "Unbekanntes Widget"}, status=HTTPStatus.NOT_FOUND
                )
            button = next(
                (item for item in widget["buttons"] if item["key"] == button_key), None
            )
            if button is None:
                return web.json_response(
                    {"error": f"Unbekannter Button '{button_key}'"}, status=HTTPStatus.NOT_FOUND
                )
            try:
                await async_run_button(hass, button)
            except Exception as err:  # noqa: BLE001 – Fehlertext an die App geben
                LOGGER.error("Aktion '%s' fehlgeschlagen: %s", button_key, err)
                return web.json_response(
                    {"error": str(err)}, status=HTTPStatus.INTERNAL_SERVER_ERROR
                )
            return web.json_response({"ok": True, "button": button_key})

        # --- Variante 2: direkter Service-Aufruf ---------------------------
        service = str(payload.get("service") or "").strip().lower()
        if not _SERVICE_RE.match(service):
            return web.json_response(
                {
                    "error": "Es wird entweder {'widget_id','button'} oder "
                    "{'service','entity_id'} erwartet"
                },
                status=HTTPStatus.BAD_REQUEST,
            )

        domain, _, service_name = service.partition(".")
        if not hass.services.has_service(domain, service_name):
            return web.json_response(
                {"error": f"Service '{service}' existiert nicht"}, status=HTTPStatus.BAD_REQUEST
            )

        data: dict[str, Any] = dict(payload.get("service_data") or {})
        entity_id = payload.get("entity_id")
        if entity_id and "entity_id" not in data:
            data["entity_id"] = entity_id

        try:
            await hass.services.async_call(domain, service_name, data, blocking=True)
        except Exception as err:  # noqa: BLE001
            LOGGER.error("Service-Aufruf '%s' fehlgeschlagen: %s", service, err)
            return web.json_response({"error": str(err)}, status=HTTPStatus.INTERNAL_SERVER_ERROR)

        return web.json_response({"ok": True, "service": service})


VIEWS: list[type[HomeAssistantView]] = [
    WidgetsView,
    WidgetView,
    SnapshotView,
    PreviewView,
    ActionView,
]
