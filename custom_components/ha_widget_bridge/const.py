"""Konstanten für HA Widget Bridge.

Die Integration ist die serverseitige „Wahrheit" für Android-Widgets:
Sie speichert die von der App angelegten Widget-Definitionen, rendert deren
Inhalt (Jinja oder automatisch aus Entities) und stellt ein kompaktes
JSON-API für die App bereit.
"""

from __future__ import annotations

import logging

DOMAIN = "ha_widget_bridge"
NAME = "HA Widget Bridge"
MANUFACTURER = "HA Widget Bridge"
MODEL = "Android-Widget"

LOGGER = logging.getLogger(__package__)

PLATFORMS: list[str] = ["sensor", "button"]

# --- Speicher -------------------------------------------------------------
STORAGE_KEY = f"{DOMAIN}.widgets"
STORAGE_VERSION = 1

# --- Signale --------------------------------------------------------------
# Definition eines Widgets hat sich geändert (anlegen/ändern/löschen)
SIGNAL_WIDGETS_UPDATED = f"{DOMAIN}_widgets_updated"
# Inhalt eines Widgets soll neu gerendert werden
SIGNAL_WIDGET_RENDERED = f"{DOMAIN}_widget_rendered"

# --- Dienste --------------------------------------------------------------
SERVICE_REFRESH = "refresh"
SERVICE_PRESS = "press"

# --- API ------------------------------------------------------------------
API_BASE = f"/api/{DOMAIN}"
URL_WIDGETS = f"{API_BASE}/widgets"
URL_WIDGET = f"{API_BASE}/widgets/{{widget_id}}"
URL_SNAPSHOT = f"{API_BASE}/widgets/{{widget_id}}/snapshot"
URL_PREVIEW = f"{API_BASE}/preview"
URL_ACTION = f"{API_BASE}/action"

# --- Grenzen --------------------------------------------------------------
MAX_WIDGETS = 25
MAX_BUTTONS = 6
MAX_VALUES = 12
MAX_TEMPLATE_LENGTH = 6000
MAX_SERVICE_DATA_KEYS = 20

# Werte-Anordnung: 1 = Liste untereinander, 2/3 = nebeneinander
VALUE_COLUMNS_MIN = 1
VALUE_COLUMNS_MAX = 3
DEFAULT_VALUE_COLUMNS = 1
# Wert unter dem Namen (zweite Zeile im Feld) statt dahinter
DEFAULT_VALUE_LABEL_ABOVE = False

TEXT_SIZE_MIN = 8.0
TEXT_SIZE_MAX = 30.0
DEFAULT_TEXT_SIZE = 14.0

# Schwellwert in W, ab dem ein Wert als "aktiv" (grün) gilt
DEFAULT_THRESHOLD = 0.5

# --- Farben (Auto-Rendering) ---------------------------------------------
COLOR_ACTIVE = "#00e676"
COLOR_IDLE = "#999999"
COLOR_ERROR = "#ff5252"

DEFAULT_THEME: dict[str, str] = {
    # Durchsichtig (Alpha 00), damit der Homescreen durchscheint
    "background": "#00000000",
    "text_color": "#FFFFFFFF",
    "accent": "#FF00E676",
    "button_background": "#26FFFFFF",
    "button_text": "#FFFFFFFF",
}

# --- Attribute ------------------------------------------------------------
ATTR_HTML = "html"
ATTR_TEXT = "text"
ATTR_WIDGET_ID = "widget_id"
ATTR_REVISION = "revision"
ATTR_UPDATED_AT = "updated_at"
ATTR_ERROR = "error"

# Zustände, die als "nicht verfügbar" gelten
UNAVAILABLE_STATES = ("unknown", "unavailable", "none", "")

# Domains, deren Zustand sinnvoll als Button-Zustand angezeigt wird
STATE_DOMAINS = (
    "switch",
    "light",
    "fan",
    "input_boolean",
    "binary_sensor",
    "cover",
    "media_player",
)

# Intervall, in dem der Inhalt-Sensor neu gerendert wird (Sekunden).
# Die Render-Engine selbst ist zustandslos, deshalb genügt ein Takt für
# Templates mit Zeitfunktionen ({{ now() }}).
CONTENT_REFRESH_SECONDS = 60
