#!/usr/bin/env python3
"""Tests für die reine Logik der Integration – ohne Home-Assistant-Installation.

Die HA-Module werden durch Minimal-Attrappen ersetzt, damit `store.py` und
`render.py` importierbar sind. So lassen sich Validierung und Formatierung
lokal prüfen (das ist genau der Teil, der später im Betrieb Fehler wirft).

Aufruf:  python3 tests/test_logik.py
"""

from __future__ import annotations

import importlib
import pathlib
import sys
import types

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACKAGE_DIR = ROOT / "custom_components" / "ha_widget_bridge"


# --------------------------------------------------------------------------
# Attrappen für Home Assistant
# --------------------------------------------------------------------------


class FakeServices:
    def __init__(self, known: set[tuple[str, str]]) -> None:
        self._known = known

    def has_service(self, domain: str, service: str) -> bool:
        return (domain, service) in self._known


class FakeStates:
    def __init__(self) -> None:
        self._states: dict[str, object] = {}

    def set(self, entity_id: str, state: str, **attributes: object) -> None:
        self._states[entity_id] = SimpleState(entity_id, state, attributes)

    def get(self, entity_id: str) -> object | None:
        return self._states.get(entity_id)


class SimpleState:
    def __init__(self, entity_id: str, state: str, attributes: dict[str, object]) -> None:
        self.entity_id = entity_id
        self.state = state
        self.attributes = attributes


class FakeHass:
    def __init__(self, known: tuple[tuple[str, str], ...] = ()) -> None:
        self.services = FakeServices(set(known))
        self.states = FakeStates()


class DummyTemplate:
    """Platzhalter für homeassistant.helpers.template.Template."""

    def __init__(self, source: str, hass: object = None) -> None:
        self.source = source

    def async_render(self, parse_result: bool = True, **kwargs: object) -> str:
        return self.source


def _register(name: str, **attributes: object) -> types.ModuleType:
    module = types.ModuleType(name)
    for key, value in attributes.items():
        setattr(module, key, value)
    sys.modules[name] = module
    return module


def _prepare_imports() -> None:
    _register("homeassistant")
    _register("homeassistant.core", HomeAssistant=object, State=SimpleState)
    _register("homeassistant.helpers")
    _register("homeassistant.helpers.storage", Store=object)
    _register("homeassistant.helpers.template", Template=DummyTemplate)
    _register("homeassistant.helpers.dispatcher", async_dispatcher_send=lambda *a, **k: None)
    _register("homeassistant.util")

    import datetime

    _register("homeassistant.util.dt", utcnow=datetime.datetime.now)

    # Die Integration als Paket registrieren, damit relative Importe
    # (`from .const import ...`) funktionieren – ohne `__init__.py` auszuführen.
    package = types.ModuleType("hawb")
    package.__path__ = [str(PACKAGE_DIR)]
    sys.modules["hawb"] = package


_prepare_imports()

store = importlib.import_module("hawb.store")
render = importlib.import_module("hawb.render")
WidgetValidationError = store.WidgetValidationError


# --------------------------------------------------------------------------
# Mini-Testrahmen
# --------------------------------------------------------------------------

failures: list[str] = []
checks = 0


def check(condition: bool, description: str) -> None:
    global checks
    checks += 1
    if not condition:
        failures.append(description)


def expect_error(payload: object, fragment: str, description: str) -> None:
    global checks
    checks += 1
    try:
        store.normalize_widget(hass, payload)
    except WidgetValidationError as err:
        if fragment not in str(err):
            failures.append(f"{description}: Fehlertext war '{err}' (erwartet: '{fragment}')")
    except Exception as err:  # noqa: BLE001
        failures.append(f"{description}: falscher Fehlertyp {type(err).__name__}: {err}")
    else:
        failures.append(f"{description}: es wurde kein Fehler gemeldet")


hass = FakeHass(known=(("switch", "toggle"), ("light", "toggle")))
hass.states.set(
    "sensor.dachboden_shelly_erik_3d_drucker_3ddrucker_leistung",
    "12.34",
    friendly_name="3D-Drucker Leistung",
    unit_of_measurement="W",
)
hass.states.set("sensor.wohnzimmer_shelly_erik_pc_leistung", "unknown")
hass.states.set("sensor.sonoff_temp_luftfeuchte_04_temperatur", "14.16", unit_of_measurement="°C")
hass.states.set("switch.wohnzimmer_shelly_erik_pc", "on")

# --------------------------------------------------------------------------
# 1) Slug-Bildung
# --------------------------------------------------------------------------

check(store.slugify("Shelly & Klima") == "shelly_klima", "slugify: Sonderzeichen")
check(store.slugify("Außen-Temperatur") == "aussen_temperatur", "slugify: Umlaute")
check(store.slugify("  ") == "", "slugify: leer")

# --------------------------------------------------------------------------
# 2) Gültige Definition (Shelly-Vorlage)
# --------------------------------------------------------------------------

gutes_widget = {
    "name": "Shelly & Klima",
    "values": [
        {"entity": "sensor.dachboden_shelly_erik_3d_drucker_3ddrucker_leistung", "label": "3D-Drucker"},
        "sensor.wohnzimmer_shelly_erik_pc_leistung",
        {"entity": "sensor.sonoff_temp_luftfeuchte_04_temperatur", "color": "#4DD0E1"},
    ],
    "buttons": [
        {
            "label": "Erik PC",
            "icon": "mdi:desktop-tower",
            "service": "switch.toggle",
            "entity_id": "switch.wohnzimmer_shelly_erik_pc",
        }
    ],
}

widget = store.normalize_widget(hass, gutes_widget)
check(widget["id"] == "shelly_klima", "ID wird aus dem Namen erzeugt")
check(len(widget["values"]) == 3, "alle drei Werte übernommen")
check(widget["values"][1]["label"] is None, "String-Wert ohne Label wird akzeptiert")
check(widget["values"][2]["color"] == "#4DD0E1", "Wert-Farbe übernommen")

button = widget["buttons"][0]
check(button["key"] == "erik_pc", "Button-Key aus Label erzeugt")
check(button["state_entity"] == "switch.wohnzimmer_shelly_erik_pc", "state_entity automatisch gesetzt")
check(widget["theme"]["accent"] == "#FF00E676", "Standard-Theme übernommen")
check(widget["theme"]["background"] == "#00000000", "Standard-Hintergrund ist durchsichtig")
check(
    store.normalize_widget(hass, {"name": "Kasten", "theme": {"background": "#E6101018"}})[
        "theme"
    ]["background"]
    == "#E6101018",
    "eigener Hintergrund wird übernommen",
)
check(widget["text_size"] == 14.0, "Standard-Schriftgröße")
check(widget["threshold"] == 0.5, "Standard-Schwellwert")
check(widget["template"] is None, "kein Template gesetzt")

# --------------------------------------------------------------------------
# 3) Validierungsfehler
# --------------------------------------------------------------------------

expect_error({"name": ""}, "name", "leerer Name wird abgelehnt")
expect_error({"name": "Test", "values": [{"entity": "kaputt"}]}, "Entity-ID", "ungültige Entity-ID")
expect_error({"name": "Test", "values": [{"entity": "sensor.a", "color": "grün"}]}, "Hex", "ungültige Farbe")
expect_error(
    {"name": "Test", "buttons": [{"label": "A", "service": "toggle"}]},
    "service",
    "Service ohne Domain",
)
expect_error(
    {"name": "Test", "buttons": [{"label": "A", "service": "switch.toggle", "icon": "hass:light"}]},
    "mdi",
    "fremdes Icon-Paket",
)
expect_error(
    {
        "name": "Test",
        "buttons": [
            {"label": "A", "service": "switch.toggle", "entity_id": "switch.a"},
            {"label": "A", "service": "switch.toggle", "entity_id": "switch.b"},
        ],
    },
    "doppelt",
    "doppelter Button-Key",
)
expect_error(
    {"name": "Test", "buttons": [{"label": str(i), "service": "switch.toggle"} for i in range(1, 9)]},
    "Maximal",
    "zu viele Buttons",
)
expect_error({"name": "Test", "values": [{"entity": "sensor.a"}], "text_size": "groß"}, "Zahl", "Textgröße keine Zahl")

# --------------------------------------------------------------------------
# 4) Rendern: Klartext, Werte, Farben
# --------------------------------------------------------------------------

check(
    render.html_to_text("<b>3D-Drucker</b> · <font color='#00e676'>12.3 W</font><br>Außen · 14.2 °C")
    == "3D-Drucker · 12.3 W\nAußen · 14.2 °C",
    "html_to_text entfernt Tags und wandelt <br> in Zeilenumbrüche",
)

text, aktiv = render.format_value(hass.states.get("sensor.dachboden_shelly_erik_3d_drucker_3ddrucker_leistung"), 0.5)
check(text == "12.3 W", f"Zahl mit einer Nachkommastelle und Einheit (war '{text}')")
check(aktiv, "12,3 W gilt als aktiv")

text, aktiv = render.format_value(hass.states.get("sensor.wohnzimmer_shelly_erik_pc_leistung"), 0.5)
check(text == "offline", "unavailable/unknown wird zu 'offline'")
check(not aktiv, "'offline' ist nicht aktiv")

text, aktiv = render.format_value(hass.states.get("switch.wohnzimmer_shelly_erik_pc"), 0.5)
check(text == "An", "Binärzustand wird eingedeutscht")
check(aktiv, "'on' gilt als aktiv")

text, _ = render.format_value(None, 0.5)
check(text == "offline", "fehlende Entity wird zu 'offline'")

check(render.color_for("offline", False) == "#ff5252", "offline ist rot")
check(render.color_for("12.3 W", True) == "#00e676", "aktiv ist grün")
check(render.color_for("0.0 W", False) == "#999999", "inaktiv ist grau")

# --------------------------------------------------------------------------
# 5) Automatische Anzeige
# --------------------------------------------------------------------------

html = render.build_auto_html(hass, widget)
check("3D-Drucker" in html, "Beschriftung erscheint")
check("friendly_name" not in html, "kein Roh-Attributname im HTML")
check(html.count("<br>") == 2, "drei Werte ergeben zwei Zeilenumbrüche")
check("<font color='#ff5252'>" in html, "unbekannter Wert wird rot dargestellt")
check("<font color='#4DD0E1'>" in html, "eigene Wertfarbe wird verwendet")
check("&lt;" not in html, "keine doppelte Escalation")

# --------------------------------------------------------------------------
# 6) Button-Zustände
# --------------------------------------------------------------------------

buttons = render.button_view(hass, widget)
check(len(buttons) == 1, "ein Button wird geliefert")
check(buttons[0]["active"] is True, "Button kennt den Zustand 'on'")
check(buttons[0]["state_label"] == "An", "Button zeigt deutschen Zustand")
check(buttons[0]["available"] is True, "Button ist verfügbar")

# --------------------------------------------------------------------------
# 7) Anordnung der Werte (Spalten / Wert unter dem Namen)
# --------------------------------------------------------------------------

import asyncio  # noqa: E402 – gehört zum Abschnitt

check(widget["value_columns"] == 1, "Standard: eine Spalte")
check(widget["value_label_above"] is False, "Standard: Wert hinter dem Namen")

zweispaltig = store.normalize_widget(
    hass, {"name": "Raster", "values": ["sensor.a", "sensor.b"], "value_columns": 2}
)
check(zweispaltig["value_columns"] == 2, "zwei Spalten werden übernommen")
check(
    store.normalize_widget(hass, {"name": "Viele", "value_columns": 9})["value_columns"] == 3,
    "zu viele Spalten werden auf 3 begrenzt",
)
check(
    store.normalize_widget(hass, {"name": "Wenig", "value_columns": 0})["value_columns"] == 1,
    "0 Spalten werden auf 1 korrigiert",
)
check(
    store.normalize_widget(hass, {"name": "Wahr", "value_label_above": "ja"})[
        "value_label_above"
    ]
    is True,
    "'ja' gilt als wahr",
)
expect_error({"name": "Test", "value_columns": "viele"}, "Zahl", "value_columns muss eine Zahl sein")
expect_error(
    {"name": "Test", "value_label_above": "vielleicht"},
    "true oder false",
    "value_label_above muss wahr/falsch sein",
)

# Wertefelder, die die App selbst anordnet
felder = render.value_view(hass, widget)
check(len(felder) == 3, "value_view liefert alle Werte")
check(felder[0]["label"] == "3D-Drucker", "value_view übernimmt das Label")
check(felder[0]["text"] == "12.3 W", "value_view formatiert den Wert")
check(felder[0]["color"] == "#00e676", "value_view liefert die Farbe")
check(felder[0]["available"] is True, "aktiver Wert ist verfügbar")
check(felder[1]["available"] is False and felder[1]["text"] == "offline", "offline erkannt")
check(felder[2]["color"] == "#4DD0E1", "eigene Wertfarbe auch in value_view")

gestapelt = dict(widget, value_label_above=True)
html_stapel = render.build_auto_html(hass, gestapelt)
check("<b>3D-Drucker</b><br>" in html_stapel, "Wert steht unter dem Namen")
check(html_stapel.count("<br>") == 5, "gestapelt: drei Werte ergeben fünf Zeilenumbrüche")

ohne_template = asyncio.run(render.async_render_widget(hass, widget))
check(ohne_template["template_used"] is False, "ohne Template ordnet die App die Werte an")
mit_template = asyncio.run(render.async_render_widget(hass, {**widget, "template": "<b>Hallo</b>"}))
check(mit_template["template_used"] is True, "mit Template gilt der HTML-Inhalt")

# --------------------------------------------------------------------------
# Zeilen (freies Layout: Text, Sensor, Button – jedes mit Ausrichtung)
# --------------------------------------------------------------------------

zeilen_widget = store.normalize_widget(
    hass,
    {
        "name": "Zeilen",
        "text_size": 13,
        "rows": [
            {
                "items": [
                    {"type": "text", "text": "Wohnzimmer", "align": "left", "size": 15},
                    {
                        "type": "sensor",
                        "entity": "sensor.wohnzimmer_shelly_erik_pc_leistung",
                        "label": "PC",
                        "align": "center",
                    },
                    {
                        "type": "button",
                        "label": "Licht",
                        "service": "switch.toggle",
                        "entity_id": "switch.wohnzimmer_shelly_erik_pc",
                        "align": "right",
                    },
                ]
            },
            {
                "items": [
                    {
                        "type": "sensor",
                        "entity": "sensor.sonoff_temp_luftfeuchte_04_temperatur",
                        "color": "#4DD0E1",
                    }
                ]
            },
        ],
    },
)
check(len(zeilen_widget["rows"]) == 2, "zwei Zeilen übernommen")
check(len(zeilen_widget["rows"][0]["items"]) == 3, "drei Objekte in der ersten Zeile")
check(zeilen_widget["rows"][0]["items"][0]["align"] == "left", "Ausrichtung je Objekt")
check(zeilen_widget["rows"][0]["items"][0]["size"] == 15.0, "Schriftgröße je Objekt")
check(zeilen_widget["rows"][0]["items"][2]["key"] == "licht", "Zeilen-Button bekommt Schlüssel")
check(
    any(button["key"] == "licht" for button in zeilen_widget["buttons"]),
    "Zeilen-Button wird als Button geführt (Drücken und Entität)",
)

zeilen = render.row_view(hass, zeilen_widget)
check(zeilen[0]["items"][0]["text"] == "Wohnzimmer", "Text-Objekt wird geliefert")
check(zeilen[0]["items"][1]["text"] != "", "Sensor-Objekt wird gerendert")
check(zeilen[0]["items"][1]["align"] == "center", "Ausrichtung bleibt erhalten")
check(zeilen[0]["items"][2]["state_label"] == "An", "Button-Objekt zeigt den Zustand")
check(zeilen[1]["items"][0]["label"] == "", "ohne Namen wird kein Name geliefert")
check(zeilen[1]["items"][0]["color"] == "#4DD0E1", "eigene Sensor-Farbe übernommen")
check(zeilen[0]["items"][1]["label"] == "PC", "eingetragener Name wird geliefert")
check(all("watch" not in row for row in zeilen),
      "Zeilen tragen keine Uhr-Kennzeichnung mehr")

# Uhr-Einstellungen: Zeilen-Auswahl, Anzahl auf der Kachel, Schriftgröße
uhr_widget = store.normalize_widget(
    hass,
    {
        "name": "Uhr",
        "rows": [
            {"items": [{"type": "text", "text": "A"}]},
            {"items": [{"type": "text", "text": "B"}]},
        ],
        "watch_rows": 99,
        "watch_scale": 9.0,
    },
)
check(uhr_widget["watch_rows"] == 8, "watch_rows wird begrenzt")
check(uhr_widget["watch_scale"] == 1.8, "watch_scale wird begrenzt")
check(len(uhr_widget["rows"]) == 2, "beide Zeilen bleiben erhalten")

uhr_render = render.row_view(hass, uhr_widget)
check(len(uhr_render) == 2, "Snapshot liefert beide Zeilen – die Uhr zeigt sie alle")
check(
    store.normalize_widget(hass, {"name": "Klein", "watch_scale": 0.1})["watch_scale"] == 0.6,
    "zu kleine Uhr-Schriftgröße wird angehoben",
)
check(store.normalize_widget(hass, {"name": "Standard"})["watch_rows"] == 0,
      "Standard: so viele Zeilen wie passen")

# Blöcke in der Zeile: Breite in Prozent und "An/Aus" abschaltbar
breiten_widget = store.normalize_widget(
    hass,
    {
        "name": "Breiten",
        "buttons": [
            {
                "key": "licht",
                "label": "Licht",
                "service": "switch.toggle",
                "entity_id": "switch.wohnzimmer_shelly_erik_pc",
                "show_state": False,
            }
        ],
        "rows": [
            {
                "items": [
                    {"type": "text", "text": "A", "width": 40},
                    {
                        "type": "button",
                        "key": "licht",
                        "label": "Licht",
                        "service": "switch.toggle",
                        "entity_id": "switch.wohnzimmer_shelly_erik_pc",
                        "width": 200,
                    },
                ]
            }
        ],
    },
)
check(breiten_widget["rows"][0]["items"][0]["width"] == 40.0, "Blockbreite übernommen")
check(breiten_widget["rows"][0]["items"][1]["width"] == 100.0, "zu große Blockbreite wird begrenzt")
check(breiten_widget["buttons"][0]["show_state"] is False, "Button kennt die An/Aus-Einstellung")
check(render.button_view(hass, breiten_widget)[0]["show_state"] is False,
      "Snapshot meldet An/Aus aus")
breiten_render = render.row_view(hass, breiten_widget)
check(breiten_render[0]["items"][1]["width"] == 100.0, "Breite kommt im Snapshot an")
check(breiten_render[0]["items"][1]["show_state"] is False,
      "An/Aus-Einstellung kommt im Snapshot an")
check(store.normalize_widget(hass, {"name": "Ohne"})["buttons"] == [],
      "ohne Buttons bleibt die Liste leer")

# Sensor in der Zeile: Wert unter dem Titel oder daneben; „An/Aus“ je Zeile
anordnung_widget = store.normalize_widget(
    hass,
    {
        "name": "Anordnung",
        "buttons": [
            {
                "key": "licht",
                "label": "Licht",
                "service": "switch.toggle",
                "entity_id": "switch.wohnzimmer_shelly_erik_pc",
            }
        ],
        "rows": [
            {
                "items": [
                    {
                        "type": "sensor",
                        "entity": "sensor.wohnzimmer_shelly_erik_pc_leistung",
                        "label": "PC",
                        "label_above": True,
                    },
                    {
                        "type": "sensor",
                        "entity": "sensor.sonoff_temp_luftfeuchte_04_temperatur",
                    },
                ]
            },
            {
                "items": [
                    {
                        "type": "button",
                        "key": "licht",
                        "label": "Licht",
                        "service": "switch.toggle",
                        "entity_id": "switch.wohnzimmer_shelly_erik_pc",
                        "show_state": False,
                    }
                ]
            },
        ],
    },
)
check(anordnung_widget["rows"][0]["items"][0]["label_above"] is True,
      "Sensor: Wert unter dem Titel wird gespeichert")
check(anordnung_widget["rows"][0]["items"][1]["label_above"] is False,
      "Sensor: Standard ist Wert neben dem Titel")
check(anordnung_widget["rows"][1]["items"][0]["show_state"] is False,
      "Zeilen-Button: An/Aus lässt sich je Zeile abschalten")
anordnung_render = render.row_view(hass, anordnung_widget)
check(anordnung_render[0]["items"][0]["label_above"] is True,
      "Wert-unten-Einstellung kommt im Snapshot an")
check(anordnung_render[1]["items"][0]["show_state"] is False,
      "eigene An/Aus-Einstellung der Zeile gewinnt")
check(anordnung_widget["buttons"][0]["show_state"] is True,
      "der Button in der Liste oben behält sein eigenes An/Aus")

# Ohne eigene Einstellung erbt der Zeilen-Button „An/Aus“ aus der Button-Liste
erbe_widget = store.normalize_widget(
    hass,
    {
        "name": "Erbe",
        "buttons": [
            {
                "key": "licht",
                "label": "Licht",
                "service": "switch.toggle",
                "entity_id": "switch.wohnzimmer_shelly_erik_pc",
                "show_state": False,
            }
        ],
        "rows": [
            {
                "items": [
                    {
                        "type": "button",
                        "key": "licht",
                        "label": "Licht",
                        "service": "switch.toggle",
                        "entity_id": "switch.wohnzimmer_shelly_erik_pc",
                    }
                ]
            }
        ],
    },
)
check(erbe_widget["rows"][0]["items"][0]["show_state"] is None,
      "Zeilen-Button ohne eigene Einstellung erbt (None)")
check(render.row_view(hass, erbe_widget)[0]["items"][0]["show_state"] is False,
      "Snapshot löst das Erbe auf: An/Aus aus der Button-Liste gilt")
expect_error(
    {"name": "Falsch", "rows": [{"items": [{"type": "sensor", "entity": "sensor.a", "label_above": "vielleicht"}]}]},
    "true oder false",
    "label_above muss wahr/falsch sein",
)

# Ziel (Handy/Uhr) und Zuordnung zu konkreten Uhren
ziel_widget = store.normalize_widget(
    hass,
    {
        "name": "Uhr-Fassung",
        "target": "watch",
        "watch_nodes": ["abc123", "abc123", "def456"],
    },
)
check(ziel_widget["target"] == "watch", "Ziel 'watch' wird gespeichert")
check(ziel_widget["watch_nodes"] == ["abc123", "def456"], "Uhren werden entdoppelt")
check(store.normalize_widget(hass, {"name": "Standard"})["target"] == "phone",
      "Standardziel ist das Handy-Widget")
check(store.normalize_widget(hass, {"name": "Alt", "target": "both"})["target"] == "phone",
      "früheres 'both' gilt jetzt als Handy-Widget")
check(store.normalize_widget(hass, {"name": "Standard"})["watch_nodes"] == [],
      "ohne Angabe sind alle Uhren gemeint")
check(store.normalize_widget(hass, {"name": "Text", "watch_nodes": "abc, def"})["watch_nodes"]
      == ["abc", "def"], "Uhrenliste darf auch als Text kommen")
check(len(store.normalize_widget(hass, {"name": "Viele", "watch_nodes": ["a", "b", "c", "d", "e"]})["watch_nodes"]) == 5,
      "bis zu fuenf Uhren erlaubt")
try:
    store.normalize_widget(hass, {"name": "Falsch", "target": "fernseher"})
    check(False, "ungueltiges Ziel wird abgelehnt")
except store.WidgetValidationError:
    check(True, "ungueltiges Ziel wird abgelehnt")
try:
    store.normalize_widget(hass, {"name": "Zuviele", "watch_nodes": ["a", "b", "c", "d", "e", "f"]})
    check(False, "zu viele Uhren werden abgelehnt")
except store.WidgetValidationError:
    check(True, "zu viele Uhren werden abgelehnt")

expect_error(
    {"name": "Test", "rows": [{"items": [{"type": "lampe"}]}]},
    "type",
    "unbekannter Objekt-Typ",
)
expect_error(
    {"name": "Test", "rows": [{"items": [{"type": "text", "align": "oben"}]}]},
    "align",
    "unbekannte Ausrichtung",
)
expect_error(
    {"name": "Test", "rows": [{"items": [{"type": "text"}] * 4}]},
    "maximal",
    "zu viele Objekte pro Zeile",
)
expect_error(
    {"name": "Test", "rows": [{"items": []}] * 9},
    "Maximal",
    "zu viele Zeilen",
)

# --------------------------------------------------------------------------
# Ergebnis
# --------------------------------------------------------------------------

if failures:
    print(f"{len(failures)} von {checks} Prüfungen fehlgeschlagen:")
    for failure in failures:
        print(f"  - {failure}")
    sys.exit(1)

print(f"OK: {checks} Prüfungen bestanden.")
