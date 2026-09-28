"""Render-Engine: erzeugt den Inhalt (HTML + Klartext) eines Widgets.

Zwei Betriebsarten:

* **Template** (Feld ``template``): serverseitig per Jinja2 gerendert – damit
  steht die komplette HA-Templating-Power zur Verfügung.
* **Automatisch** (Feld ``values``): pro Entity eine Zeile
  ``Label · Wert``, eingefärbt nach Zustand (aktiv / inaktiv / offline).

Die App rendert selbst nichts, sie zeigt nur an, was hier entsteht.
"""

from __future__ import annotations

import html as html_lib
import re
from typing import Any

from homeassistant.core import HomeAssistant, State
from homeassistant.helpers.template import Template

from .const import (
    COLOR_ACTIVE,
    COLOR_ERROR,
    COLOR_IDLE,
    DEFAULT_THRESHOLD,
    LOGGER,
    UNAVAILABLE_STATES,
)

_TAG_RE = re.compile(r"<[^>]+>")
_BR_RE = re.compile(r"(?i)<br\s*/?>")
_P_RE = re.compile(r"(?i)</p>")

# Zustände, die als "aktiv" gelten (Button-Farbe / grüner Wert)
ACTIVE_STATES = frozenset(
    {
        "on",
        "open",
        "opening",
        "home",
        "playing",
        "heat",
        "cool",
        "heat_cool",
        "charging",
        "cleaning",
        "unlocked",
        "detected",
    }
)

# Deutsche Kurztexte für Binärzustände
STATE_WORDS = {
    "on": "An",
    "off": "Aus",
    "open": "Offen",
    "closed": "Geschlossen",
    "opening": "Öffnet",
    "closing": "Schließt",
    "home": "Zuhause",
    "not_home": "Unterwegs",
    "playing": "Läuft",
    "paused": "Pausiert",
    "idle": "Bereit",
    "unavailable": "offline",
    "unknown": "unbekannt",
    "unlocked": "Entriegelt",
    "locked": "Verriegelt",
    "charging": "Lädt",
}


def html_to_text(value: str) -> str:
    """HTML in Klartext wandeln (für die State-Anzeige in Home Assistant)."""
    text = _BR_RE.sub("\n", value)
    text = _P_RE.sub("\n", text)
    text = _TAG_RE.sub("", text)
    text = html_lib.unescape(text).replace("\xa0", " ")
    return "\n".join(line.strip() for line in text.splitlines() if line.strip())


def display_name(hass: HomeAssistant, entity_id: str) -> str:
    """Friendly Name eines Entities, sonst ein lesbarer Ersatzname."""
    state = hass.states.get(entity_id)
    if state is not None:
        name = state.attributes.get("friendly_name")
        if name:
            return str(name)
    return entity_id.split(".", 1)[-1].replace("_", " ").title()


def format_value(state: State | None, threshold: float) -> tuple[str, bool]:
    """Wert für die Anzeige formatieren. Rückgabe: (Text, aktiv)."""
    if state is None or str(state.state).lower() in UNAVAILABLE_STATES:
        return "offline", False

    raw = str(state.state)
    unit = state.attributes.get("unit_of_measurement") or ""

    try:
        number = float(raw)
    except (TypeError, ValueError):
        label = STATE_WORDS.get(raw.lower(), raw)
        return f"{label} {unit}".strip(), raw.lower() in ACTIVE_STATES

    return f"{number:.1f} {unit}".strip(), number >= threshold


def color_for(text: str, active: bool) -> str:
    """Farbe passend zum Zustand."""
    if text == "offline":
        return COLOR_ERROR
    return COLOR_ACTIVE if active else COLOR_IDLE


def build_auto_html(hass: HomeAssistant, widget: dict[str, Any]) -> str:
    """Anzeige aus ``values`` erzeugen (eine Zeile pro Entity)."""
    default_threshold = float(widget.get("threshold", DEFAULT_THRESHOLD))
    lines: list[str] = []

    for value in widget.get("values") or []:
        entity_id = value["entity"]
        state = hass.states.get(entity_id)
        name = value.get("label") or display_name(hass, entity_id)

        limit = value.get("threshold")
        limit = default_threshold if limit is None else float(limit)

        text, active = format_value(state, limit)

        # Eigene Farbe pro Wert hat Vorrang (offline bleibt rot)
        own_color = value.get("color")
        color = own_color if own_color and text != "offline" else color_for(text, active)

        lines.append(
            f"<b>{html_lib.escape(name)}</b> · "
            f"<font color='{color}'>{html_lib.escape(text)}</font>"
        )

    return "<br>".join(lines)


async def async_render_widget(hass: HomeAssistant, widget: dict[str, Any]) -> dict[str, Any]:
    """Inhalt eines Widgets rendern.

    Rückgabe: ``{"html": str, "text": str, "error": str | None}``.
    """
    error: str | None = None
    html_out = ""

    template_source = widget.get("template")
    if template_source:
        try:
            template = Template(template_source, hass)
            rendered = template.async_render(parse_result=False)
            html_out = rendered if isinstance(rendered, str) else str(rendered)
        except Exception as err:  # noqa: BLE001 – Fehler soll im Widget sichtbar sein
            error = f"Template-Fehler: {err}"
            LOGGER.warning("Widget '%s': %s", widget.get("id"), error)

    if not html_out.strip():
        html_out = build_auto_html(hass, widget)

    if error:
        html_out = f"<font color='{COLOR_ERROR}'>{html_lib.escape(error)}</font><br>{html_out}"

    return {"html": html_out, "text": html_to_text(html_out), "error": error}


def button_view(hass: HomeAssistant, widget: dict[str, Any]) -> list[dict[str, Any]]:
    """Buttons inkl. aktuellem Zustand für die App aufbereiten."""
    result: list[dict[str, Any]] = []

    for button in widget.get("buttons") or []:
        entity_id = button.get("state_entity")
        state = hass.states.get(entity_id) if entity_id else None

        available = state is not None and str(state.state).lower() not in UNAVAILABLE_STATES
        raw = str(state.state).lower() if state is not None else ""

        if not available:
            state_label = "offline"
        elif raw in ACTIVE_STATES:
            state_label = button.get("state_label_on") or STATE_WORDS.get(raw, raw)
        else:
            state_label = button.get("state_label_off") or STATE_WORDS.get(raw, raw)

        result.append(
            {
                "key": button["key"],
                "label": button["label"],
                "icon": button.get("icon"),
                "entity_id": button.get("entity_id"),
                "state_entity": entity_id,
                "state": state.state if state is not None else None,
                "state_label": state_label,
                "active": available and raw in ACTIVE_STATES,
                "available": available,
            }
        )

    return result
