"""Persistente Ablage der Widget-Definitionen.

Die Definitionen werden von der Android-App über das JSON-API gepflegt und in
``.storage/ha_widget_bridge.widgets`` abgelegt. Dadurch überleben sie
App-Neuinstallation und Handywechsel.
"""

from __future__ import annotations

import re
from typing import Any

from homeassistant.core import HomeAssistant
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store
from homeassistant.util import dt as dt_util

from .const import (
    DEFAULT_ROW_ALIGN,
    DEFAULT_THEME,
    DEFAULT_TEXT_SIZE,
    DEFAULT_THRESHOLD,
    DEFAULT_VALUE_COLUMNS,
    DEFAULT_VALUE_LABEL_ABOVE,
    DEFAULT_WATCH_ROWS,
    DEFAULT_WATCH_SCALE,
    DEFAULT_TARGET,
    DOMAIN,
    LOGGER,
    MAX_BUTTONS,
    MAX_ROWS,
    MAX_ROW_ITEMS,
    MAX_SERVICE_DATA_KEYS,
    MAX_TEMPLATE_LENGTH,
    MAX_VALUES,
    MAX_WIDGETS,
    MAX_WATCH_NODES,
    LEGACY_TARGETS,
    ROW_ALIGNMENTS,
    ROW_ITEM_TYPES,
    SIGNAL_WIDGETS_UPDATED,
    STATE_DOMAINS,
    STORAGE_KEY,
    STORAGE_VERSION,
    TEXT_SIZE_MAX,
    TEXT_SIZE_MIN,
    VALUE_COLUMNS_MAX,
    VALUE_COLUMNS_MIN,
    WATCH_NODE_LENGTH,
    WATCH_ROWS_MAX,
    WATCH_ROWS_MIN,
    WATCH_SCALE_MAX,
    WATCH_SCALE_MIN,
    WIDGET_TARGETS,
)

_SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,49}$")
_SERVICE_RE = re.compile(r"^[a-z0-9_]+\.[a-z0-9_]+$")
_ENTITY_RE = re.compile(r"^[a-z0-9_]+\.[a-z0-9_]+$")
_ICON_RE = re.compile(r"^mdi:[a-z0-9-]+$")
_COLOR_RE = re.compile(r"^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")


class WidgetValidationError(ValueError):
    """Die übergebene Widget-Definition ist ungültig."""


def _as_bool(value: Any, field: str) -> bool:
    """Wahrheitswert annehmen (bool, 0/1 oder Text wie 'true'/'ja')."""
    if isinstance(value, bool):
        return value
    if value in (None, ""):
        return False
    if isinstance(value, (int, float)):
        return bool(value)
    text = str(value).strip().lower()
    if text in ("true", "1", "yes", "on", "ja"):
        return True
    if text in ("false", "0", "no", "off", "nein"):
        return False
    raise WidgetValidationError(f"{field} muss true oder false sein")


def slugify(value: Any) -> str:
    """Aus beliebigem Text eine ID/einen Schlüssel im Slug-Format machen."""
    text = str(value or "").strip().lower()
    text = text.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    text = re.sub(r"[^a-z0-9]+", "_", text)
    return text.strip("_")[:50]


def _check_entity_id(value: Any, field: str) -> str:
    entity_id = str(value or "").strip()
    if not _ENTITY_RE.match(entity_id):
        raise WidgetValidationError(f"{field}: '{entity_id}' ist keine gültige Entity-ID")
    return entity_id


def _normalize_entity_list(value: Any, field: str) -> str | None:
    """Entity-ID(s) annehmen und als kommagetrennten String zurückgeben."""
    if value in (None, "", []):
        return None
    items = value if isinstance(value, (list, tuple)) else [value]
    checked = [_check_entity_id(item, field) for item in items]
    return ",".join(checked) if checked else None


def _normalize_values(raw: Any) -> list[dict[str, Any]]:
    if raw in (None, ""):
        return []
    if not isinstance(raw, list):
        raise WidgetValidationError("'values' muss eine Liste sein")
    if len(raw) > MAX_VALUES:
        raise WidgetValidationError(f"Maximal {MAX_VALUES} Werte pro Widget")

    values: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in raw:
        if isinstance(item, str):
            item = {"entity": item}
        if not isinstance(item, dict):
            raise WidgetValidationError("Jeder Wert muss ein Objekt oder eine Entity-ID sein")

        entity_id = _check_entity_id(item.get("entity"), "values.entity")
        if entity_id in seen:
            continue
        seen.add(entity_id)

        label = str(item.get("label") or "").strip()[:40] or None

        threshold: float | None = None
        if item.get("threshold") is not None:
            try:
                threshold = float(item["threshold"])
            except (TypeError, ValueError) as err:
                raise WidgetValidationError("values.threshold muss eine Zahl sein") from err

        color = str(item.get("color") or "").strip() or None
        if color and not _COLOR_RE.match(color):
            raise WidgetValidationError(f"values.color '{color}' ist keine Hex-Farbe")

        values.append(
            {
                "entity": entity_id,
                "label": label,
                "threshold": threshold,
                "color": color,
            }
        )
    return values


def _normalize_button(hass: HomeAssistant, item: Any, field: str) -> dict[str, Any]:
    """Einen Button prüfen und in die kanonische Form bringen."""
    if not isinstance(item, dict):
        raise WidgetValidationError(f"Jeder {field}-Eintrag muss ein Objekt sein")

    label = str(item.get("label") or "").strip()[:30]
    if not label:
        raise WidgetValidationError(f"{field}.label fehlt")

    key = slugify(item.get("key") or label)
    if not key or not _SLUG_RE.match(key):
        raise WidgetValidationError(f"{field}.key '{key}' ist ungültig")

    service = str(item.get("service") or "").strip().lower()
    if not _SERVICE_RE.match(service):
        raise WidgetValidationError(
            f"{field}.service '{service}' ist ungültig (erwartet z. B. 'switch.toggle')"
        )
    domain, _, service_name = service.partition(".")
    if not hass.services.has_service(domain, service_name):
        LOGGER.warning(
            "Button '%s' (%s) nutzt den Service '%s', der aktuell nicht existiert",
            key,
            field,
            service,
        )

    entity_id = _normalize_entity_list(item.get("entity_id"), f"{field}.entity_id")

    state_entity = item.get("state_entity")
    if state_entity:
        state_entity = _check_entity_id(state_entity, f"{field}.state_entity")
    elif entity_id and "," not in entity_id and entity_id.split(".", 1)[0] in STATE_DOMAINS:
        state_entity = entity_id

    service_data = item.get("service_data") or {}
    if not isinstance(service_data, dict):
        raise WidgetValidationError(f"{field}.service_data muss ein Objekt sein")
    if len(service_data) > MAX_SERVICE_DATA_KEYS:
        raise WidgetValidationError(f"{field}.service_data hat zu viele Felder")
    if domain not in ("homeassistant", "script", "automation") and not entity_id:
        LOGGER.warning("Button '%s' (%s) hat keine entity_id", key, service)

    icon = str(item.get("icon") or "").strip() or None
    if icon and not _ICON_RE.match(icon):
        raise WidgetValidationError(f"{field}.icon '{icon}' ist kein mdi:-Icon")

    return {
        "key": key,
        "label": label,
        "icon": icon,
        "service": service,
        "entity_id": entity_id,
        "state_entity": state_entity,
        # "An/Aus" im Widget anzeigen?
        "show_state": _as_bool(item.get("show_state", True), f"{field}.show_state"),
        "state_label_on": str(item.get("state_label_on") or "").strip()[:20] or None,
        "state_label_off": str(item.get("state_label_off") or "").strip()[:20] or None,
        "service_data": {str(k): v for k, v in service_data.items()},
    }


def _normalize_buttons(hass: HomeAssistant, raw: Any) -> list[dict[str, Any]]:
    if raw in (None, ""):
        return []
    if not isinstance(raw, list):
        raise WidgetValidationError("'buttons' muss eine Liste sein")
    if len(raw) > MAX_BUTTONS:
        raise WidgetValidationError(f"Maximal {MAX_BUTTONS} Buttons pro Widget")

    buttons: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in raw:
        button = _normalize_button(hass, item, "buttons")
        if button["key"] in seen:
            raise WidgetValidationError(f"buttons.key '{button['key']}' kommt doppelt vor")
        seen.add(button["key"])
        buttons.append(button)
    return buttons


def _normalize_rows(hass: HomeAssistant, raw: Any) -> list[dict[str, Any]]:
    """Zeilen mit Objekten (Text, Sensor, Button) prüfen."""
    if raw in (None, ""):
        return []
    if not isinstance(raw, list):
        raise WidgetValidationError("'rows' muss eine Liste sein")
    if len(raw) > MAX_ROWS:
        raise WidgetValidationError(f"Maximal {MAX_ROWS} Zeilen pro Widget")

    rows: list[dict[str, Any]] = []
    seen_keys: set[str] = set()

    for number, row in enumerate(raw, start=1):
        if not isinstance(row, dict):
            raise WidgetValidationError(f"Zeile {number} muss ein Objekt sein")

        raw_items = row.get("items")
        if raw_items in (None, ""):
            raw_items = []
        if not isinstance(raw_items, list):
            raise WidgetValidationError(f"Zeile {number}: 'items' muss eine Liste sein")
        if len(raw_items) > MAX_ROW_ITEMS:
            raise WidgetValidationError(f"Zeile {number}: maximal {MAX_ROW_ITEMS} Objekte")

        items = [_normalize_row_item(hass, item, number, seen_keys) for item in raw_items]
        rows.append({"items": items})

    return rows


def _normalize_row_item(
    hass: HomeAssistant, item: Any, row: int, seen_keys: set[str]
) -> dict[str, Any]:
    """Ein Objekt innerhalb einer Zeile prüfen."""
    if not isinstance(item, dict):
        raise WidgetValidationError(f"Zeile {row}: jedes Objekt muss ein Objekt sein")

    kind = str(item.get("type") or "text").strip().lower()
    if kind not in ROW_ITEM_TYPES:
        raise WidgetValidationError(
            f"Zeile {row}: type muss {'/'.join(ROW_ITEM_TYPES)} sein"
        )

    align = str(item.get("align") or DEFAULT_ROW_ALIGN).strip().lower()
    if align not in ROW_ALIGNMENTS:
        raise WidgetValidationError(f"Zeile {row}: align muss {'/'.join(ROW_ALIGNMENTS)} sein")

    try:
        size = float(item.get("size") or DEFAULT_TEXT_SIZE)
    except (TypeError, ValueError) as err:
        raise WidgetValidationError(f"Zeile {row}: size muss eine Zahl sein") from err
    size = min(max(size, TEXT_SIZE_MIN), TEXT_SIZE_MAX)

    color = str(item.get("color") or "").strip() or None
    if color and not _COLOR_RE.match(color):
        raise WidgetValidationError(f"Zeile {row}: color muss eine Hex-Farbe sein")

    # Breite des Blocks innerhalb der Zeile (Prozent, 0 = gleiche Anteile)
    try:
        width = float(item.get("width") or 0)
    except (TypeError, ValueError) as err:
        raise WidgetValidationError(f"Zeile {row}: width muss eine Zahl sein") from err
    width = round(min(max(width, 0.0), 100.0), 1)

    if kind == "text":
        return {
            "type": "text",
            "text": str(item.get("text") or "")[:80],
            "align": align,
            "size": round(size, 1),
            "width": width,
            "color": color,
        }

    if kind == "sensor":
        threshold: float | None = None
        if item.get("threshold") is not None:
            try:
                threshold = float(item["threshold"])
            except (TypeError, ValueError) as err:
                raise WidgetValidationError(
                    f"Zeile {row}: threshold muss eine Zahl sein"
                ) from err
        return {
            "type": "sensor",
            "entity": _check_entity_id(item.get("entity"), f"Zeile {row}.entity"),
            "label": str(item.get("label") or "").strip()[:40] or None,
            "align": align,
            "size": round(size, 1),
            "width": width,
            "color": color,
            "threshold": threshold,
        }

    button = _normalize_button(hass, item, f"Zeile {row}")
    if button["key"] in seen_keys:
        raise WidgetValidationError(
            f"Zeile {row}: Schlüssel '{button['key']}' kommt doppelt vor"
        )
    seen_keys.add(button["key"])
    button.update({
        "type": "button",
        "align": align,
        "size": round(size, 1),
        "width": width,
        "color": color,
    })
    return button


def _normalize_theme(raw: Any) -> dict[str, str]:
    theme = dict(DEFAULT_THEME)
    if not isinstance(raw, dict):
        return theme
    for key in DEFAULT_THEME:
        value = raw.get(key)
        if value in (None, ""):
            continue
        value = str(value).strip()
        if not _COLOR_RE.match(value):
            raise WidgetValidationError(f"theme.{key} muss eine Hex-Farbe sein (z. B. #FF00E676)")
        theme[key] = value
    return theme


def normalize_widget(hass: HomeAssistant, payload: Any) -> dict[str, Any]:
    """Widget-Definition prüfen und in die kanonische Form bringen."""
    if not isinstance(payload, dict):
        raise WidgetValidationError("Es wurde kein Objekt übergeben")

    name = str(payload.get("name") or "").strip()[:80]
    if not name:
        raise WidgetValidationError("Feld 'name' fehlt")

    widget_id = str(payload.get("id") or slugify(name)).strip().lower()
    if not _SLUG_RE.match(widget_id):
        raise WidgetValidationError(
            f"Feld 'id' '{widget_id}' ist ungültig (a-z, 0-9, _ und -)"
        )

    template: str | None = None
    if payload.get("template") not in (None, ""):
        template = str(payload["template"])
        if len(template) > MAX_TEMPLATE_LENGTH:
            raise WidgetValidationError(
                f"'template' darf maximal {MAX_TEMPLATE_LENGTH} Zeichen lang sein"
            )
        template = template.strip() or None

    try:
        text_size = float(payload.get("text_size", DEFAULT_TEXT_SIZE))
    except (TypeError, ValueError) as err:
        raise WidgetValidationError("text_size muss eine Zahl sein") from err
    text_size = min(max(text_size, TEXT_SIZE_MIN), TEXT_SIZE_MAX)

    threshold = payload.get("threshold", DEFAULT_THRESHOLD)
    try:
        threshold = float(threshold)
    except (TypeError, ValueError) as err:
        raise WidgetValidationError("threshold muss eine Zahl sein") from err

    try:
        value_columns = int(payload.get("value_columns", DEFAULT_VALUE_COLUMNS))
    except (TypeError, ValueError) as err:
        raise WidgetValidationError("value_columns muss eine Zahl sein") from err
    value_columns = min(max(value_columns, VALUE_COLUMNS_MIN), VALUE_COLUMNS_MAX)

    value_label_above = _as_bool(
        payload.get("value_label_above", DEFAULT_VALUE_LABEL_ABOVE), "value_label_above"
    )

    rows = _normalize_rows(hass, payload.get("rows"))

    try:
        watch_rows = int(payload.get("watch_rows", DEFAULT_WATCH_ROWS))
    except (TypeError, ValueError) as err:
        raise WidgetValidationError("watch_rows muss eine Zahl sein") from err
    watch_rows = min(max(watch_rows, WATCH_ROWS_MIN), WATCH_ROWS_MAX)

    try:
        watch_scale = float(payload.get("watch_scale", DEFAULT_WATCH_SCALE))
    except (TypeError, ValueError) as err:
        raise WidgetValidationError("watch_scale muss eine Zahl sein") from err
    watch_scale = min(max(watch_scale, WATCH_SCALE_MIN), WATCH_SCALE_MAX)

    # Ziel: Widget für den Homescreen ("phone") oder Fassung für die Uhr ("watch")
    target = str(payload.get("target") or DEFAULT_TARGET).strip().lower()
    # "both" war die frühere gemeinsame Fassung – sie zählt jetzt als Handy-Widget
    target = LEGACY_TARGETS.get(target, target)
    if target not in WIDGET_TARGETS:
        raise WidgetValidationError(f"target muss {'/'.join(WIDGET_TARGETS)} sein")

    # Wear-Knoten (Uhren), auf denen dieses Widget erscheinen soll
    raw_nodes = payload.get("watch_nodes") or []
    if isinstance(raw_nodes, str):
        raw_nodes = [part.strip() for part in raw_nodes.split(",") if part.strip()]
    if not isinstance(raw_nodes, list):
        raise WidgetValidationError("'watch_nodes' muss eine Liste sein")
    watch_nodes: list[str] = []
    for node in raw_nodes:
        node_id = str(node).strip()[:WATCH_NODE_LENGTH]
        if node_id and node_id not in watch_nodes:
            watch_nodes.append(node_id)
    if len(watch_nodes) > MAX_WATCH_NODES:
        raise WidgetValidationError(f"Maximal {MAX_WATCH_NODES} Uhren pro Widget")
    buttons = _normalize_buttons(hass, payload.get("buttons"))
    # Buttons aus den Zeilen mit aufnehmen: nur so entstehen die
    # button.<widget>_<key>-Entitäten und das Drücken funktioniert.
    known = {button["key"] for button in buttons}
    for row in rows:
        for item in row["items"]:
            if item["type"] == "button" and item["key"] not in known:
                known.add(item["key"])
                buttons.append(item)
    if len(buttons) > MAX_BUTTONS:
        raise WidgetValidationError(f"Maximal {MAX_BUTTONS} Buttons pro Widget")

    return {
        "id": widget_id,
        "name": name,
        "template": template,
        "values": _normalize_values(payload.get("values")),
        "buttons": buttons,
        "rows": rows,
        "watch_rows": watch_rows,
        "watch_scale": round(watch_scale, 2),
        "target": target,
        "watch_nodes": watch_nodes,
        "theme": _normalize_theme(payload.get("theme")),
        "text_size": round(text_size, 1),
        "threshold": threshold,
        "value_columns": value_columns,
        "value_label_above": value_label_above,
    }


def _public(widget: dict[str, Any]) -> dict[str, Any]:
    """Definition ohne interne Felder für die API zurückgeben."""
    return {key: value for key, value in widget.items() if not key.startswith("_")}


class WidgetStore:
    """Hält alle Widget-Definitionen im Speicher und in ``.storage``."""

    def __init__(self, hass: HomeAssistant) -> None:
        self.hass = hass
        self._store: Store = Store(hass, STORAGE_VERSION, STORAGE_KEY)
        self._widgets: dict[str, dict[str, Any]] = {}

    # -- Laden / Speichern -------------------------------------------------
    async def async_load(self) -> None:
        data = await self._store.async_load() or {}
        raw_widgets = data.get("widgets") if isinstance(data, dict) else None
        self._widgets = {}
        if not isinstance(raw_widgets, list):
            return
        for raw in raw_widgets:
            try:
                widget = normalize_widget(self.hass, raw)
            except WidgetValidationError as err:
                LOGGER.warning("Gespeichertes Widget wird übersprungen: %s", err)
                continue
            widget["revision"] = int(raw.get("revision") or 0)
            widget["updated_at"] = raw.get("updated_at") or dt_util.utcnow().isoformat()
            self._widgets[widget["id"]] = widget

    async def async_save(self) -> None:
        await self._store.async_save({"widgets": list(self._widgets.values())})

    # -- Zugriff -----------------------------------------------------------
    def as_list(self) -> list[dict[str, Any]]:
        return [_public(widget) for widget in self._widgets.values()]

    def get(self, widget_id: str) -> dict[str, Any] | None:
        widget = self._widgets.get(widget_id)
        return _public(widget) if widget else None

    def count(self) -> int:
        return len(self._widgets)

    # -- Ändern ------------------------------------------------------------
    async def async_upsert(self, payload: Any) -> dict[str, Any]:
        widget = normalize_widget(self.hass, payload)
        widget_id = widget["id"]
        previous = self._widgets.get(widget_id)

        if previous is None and len(self._widgets) >= MAX_WIDGETS:
            raise WidgetValidationError(f"Maximal {MAX_WIDGETS} Widgets")

        widget["revision"] = int(previous["revision"]) + 1 if previous else 1
        widget["updated_at"] = dt_util.utcnow().isoformat()

        self._widgets[widget_id] = widget
        await self.async_save()
        LOGGER.debug("Widget '%s' gespeichert (Revision %s)", widget_id, widget["revision"])
        async_dispatcher_send(self.hass, SIGNAL_WIDGETS_UPDATED, widget_id)
        return _public(widget)

    async def async_delete(self, widget_id: str) -> bool:
        if widget_id not in self._widgets:
            return False
        del self._widgets[widget_id]
        await self.async_save()
        LOGGER.debug("Widget '%s' gelöscht", widget_id)
        async_dispatcher_send(self.hass, SIGNAL_WIDGETS_UPDATED, widget_id)
        return True


def get_store(hass: HomeAssistant) -> WidgetStore | None:
    """Store aus ``hass.data`` holen (``None``, wenn nicht eingerichtet)."""
    bucket = hass.data.get(DOMAIN)
    if not isinstance(bucket, dict):
        return None
    store = bucket.get("store")
    return store if isinstance(store, WidgetStore) else None
