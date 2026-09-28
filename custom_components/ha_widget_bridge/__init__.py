"""HA Widget Bridge – serverseitige Widget-Definitionen und Render-API.

Die Integration ist bewusst schlank: Sie speichert die von der Android-App
angelegten Widget-Definitionen, rendert deren Inhalt serverseitig (Jinja oder
automatisch aus Entities), macht jedes Widget als HA-Gerät mit Entities
sichtbar und stellt ein kompaktes JSON-API für die App bereit.
"""

from __future__ import annotations

import voluptuous as vol

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.typing import ConfigType

from .actions import async_run_button
from .const import (
    DOMAIN,
    LOGGER,
    PLATFORMS,
    SERVICE_PRESS,
    SERVICE_REFRESH,
    SIGNAL_WIDGET_RENDERED,
)
from .http import VIEWS as HTTP_VIEWS
from .store import WidgetStore, get_store

try:  # HA >= 2024.6
    CONFIG_SCHEMA = cv.config_entry_only_config_schema(DOMAIN)
except AttributeError:  # pragma: no cover – ältere Versionen
    CONFIG_SCHEMA = vol.Schema({}, extra=vol.ALLOW_EXTRA)

try:
    from homeassistant.exceptions import ServiceValidationError as _ServiceError
except ImportError:  # pragma: no cover – ältere Versionen
    from homeassistant.exceptions import HomeAssistantError as _ServiceError

SERVICE_REFRESH_SCHEMA = vol.Schema({vol.Optional("widget_id"): cv.string})
SERVICE_PRESS_SCHEMA = vol.Schema(
    {
        vol.Required("widget_id"): cv.string,
        vol.Required("button"): cv.string,
    }
)


async def async_setup(hass: HomeAssistant, config: ConfigType) -> bool:
    """Domänen-Setup: die HTTP-Views genau einmal registrieren."""
    bucket = hass.data.setdefault(DOMAIN, {})

    if not bucket.get("views_registered"):
        for view in HTTP_VIEWS:
            hass.http.register_view(view())
        bucket["views_registered"] = True
        LOGGER.debug("HTTP-Views unter /api/%s registriert", DOMAIN)

    return True


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Config-Entry einrichten (ein Eintrag verwaltet alle Widgets)."""
    bucket = hass.data.setdefault(DOMAIN, {})

    store = WidgetStore(hass)
    await store.async_load()
    bucket["store"] = store

    _async_register_services(hass)

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)

    LOGGER.info("HA Widget Bridge bereit – %s Widget(s) hinterlegt", store.count())
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Config-Entry entfernen."""
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unloaded:
        hass.data.get(DOMAIN, {}).pop("store", None)
    return unloaded


async def async_reload_entry(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Nach einem Options-Update neu laden."""
    await hass.config_entries.async_reload(entry.entry_id)


def _async_register_services(hass: HomeAssistant) -> None:
    """Dienste einmalig registrieren."""
    if hass.services.has_service(DOMAIN, SERVICE_REFRESH):
        return

    async def _handle_refresh(call: ServiceCall) -> None:
        store = get_store(hass)
        if store is None:
            raise _ServiceError("HA Widget Bridge ist nicht eingerichtet")

        widget_id = call.data.get("widget_id")
        if widget_id:
            async_dispatcher_send(hass, SIGNAL_WIDGET_RENDERED, widget_id)
            return
        for widget in store.as_list():
            async_dispatcher_send(hass, SIGNAL_WIDGET_RENDERED, widget["id"])

    async def _handle_press(call: ServiceCall) -> None:
        store = get_store(hass)
        widget = store.get(call.data["widget_id"]) if store else None
        if widget is None:
            raise _ServiceError(f"Unbekanntes Widget '{call.data['widget_id']}'")

        button = next(
            (item for item in widget["buttons"] if item["key"] == call.data["button"]), None
        )
        if button is None:
            raise _ServiceError(f"Unbekannter Button '{call.data['button']}'")

        await async_run_button(hass, button)
        async_dispatcher_send(hass, SIGNAL_WIDGET_RENDERED, widget["id"])

    hass.services.async_register(
        DOMAIN, SERVICE_REFRESH, _handle_refresh, schema=SERVICE_REFRESH_SCHEMA
    )
    hass.services.async_register(
        DOMAIN, SERVICE_PRESS, _handle_press, schema=SERVICE_PRESS_SCHEMA
    )
