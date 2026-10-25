#!/usr/bin/env python3
"""Trägt Außentemperatur und Luftfeuchte in die vorhandenen Widgets ein.

Die Widget-Definitionen liegen nicht im Repo, sondern in Home Assistant
(``.storage/ha_widget_bridge.widgets``) und werden über die JSON-API der
Integration gepflegt.

Das Widget besteht seit der Zeilen-Umstellung aus ``rows``; die alte Liste
``values`` wird von der App nicht mehr gerendert. Dieses Skript arbeitet
deshalb auf beiden Formen (idempotent):

* Sensor-Objekte in ``rows``, die einen Innen-Sensor der Sonoff-Familie
  (``sensor.sonoff_temp_luftfeuchte_<raum>_temperatur`` bzw.
  ``…_luftfeuchtigkeit``) zeigen, werden auf die Außen-Entitäten umgestellt.
* Fehlt eine Temperatur-/Feuchte-Zeile ganz, wird sie am Ende ergänzt.
* Die Liste ``values`` (Abschnitt „Werte“ in der App) bekommt alle Sensoren aus
  den Zeilen; Innen-Sensoren werden dabei auf die Außenwerte umgestellt.

    sensor.sonoff_temp_luftfeuchte_aussen_temperatur         Label "Außen"
    sensor.sonoff_temp_luftfeuchte_aussen_luftfeuchtigkeit   Label "Außenfeuchte"

Aufruf:
    python3 tools/set_outside_row.py                 # schreiben
    python3 tools/set_outside_row.py --dry-run       # nur zeigen
    python3 tools/set_outside_row.py --all           # auch Uhr-Fassungen

Zugang:
    HASS_TOKEN=<token>            oder Datei ``~/.ha-token`` (nur das Token)
    HA_URL=<url>                  Standard http://192.168.40.117
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_URL = os.environ.get("HA_URL", "http://192.168.40.117")
TOKEN_FILE = Path.home() / ".ha-token"
API = "/api/ha_widget_bridge/widgets"

TEMP_ENTITY = "sensor.sonoff_temp_luftfeuchte_aussen_temperatur"
HUM_ENTITY = "sensor.sonoff_temp_luftfeuchte_aussen_luftfeuchtigkeit"
TEMP_LABEL = "Außen"
HUM_LABEL = "Außenfeuchte"
COLOR = "#4DD0E1"

# Alte Werte-Liste: Label -> Entity, Farbe wie im Preset
OUTSIDE = [
    (TEMP_LABEL, TEMP_ENTITY, COLOR),
    (HUM_LABEL, HUM_ENTITY, COLOR),
]

INDOOR_PREFIX = "sensor.sonoff_temp_luftfeuchte_"


def kind_of(entity: str) -> str | None:
    """'temp'/'hum' für Innen-Sensoren der Sonoff-Familie, sonst None."""
    if not entity.startswith(INDOOR_PREFIX) or entity in (TEMP_ENTITY, HUM_ENTITY):
        return None
    if entity.endswith("_temperatur"):
        return "temp"
    if entity.endswith("_luftfeuchtigkeit"):
        return "hum"
    return None


def sensor_item() -> dict:
    return {
        "type": "sensor",
        "entity": TEMP_ENTITY,
        "label": TEMP_LABEL,
        "align": "left",
        "size": 14.0,
        "width": 50.0,
        "color": COLOR,
        "threshold": None,
        "label_above": False,
    }


def patch_rows(widget: dict) -> bool:
    """Innen-Sensoren in den Zeilen durch die Außenwerte ersetzen."""
    changed = False
    have_temp = have_hum = False

    for row in widget.get("rows") or []:
        for item in row.get("items") or []:
            if item.get("type") != "sensor":
                continue
            entity = str(item.get("entity") or "")
            if entity == TEMP_ENTITY:
                have_temp = True
            if entity == HUM_ENTITY:
                have_hum = True

            kind = kind_of(entity)
            if kind is None:
                continue
            if kind == "temp":
                item.update(entity=TEMP_ENTITY, label=TEMP_LABEL, color=COLOR)
                have_temp = True
            else:
                item.update(entity=HUM_ENTITY, label=HUM_LABEL, color=COLOR)
                have_hum = True
            changed = True

    if have_temp and have_hum:
        return changed

    rows = widget.get("rows")
    if not isinstance(rows, list):
        rows = []
        widget["rows"] = rows
    if len(rows) >= 8:  # MAX_ROWS der Integration
        return changed

    items = []
    if not have_temp:
        items.append(sensor_item())
    if not have_hum:
        hum = sensor_item()
        hum.update(entity=HUM_ENTITY, label=HUM_LABEL, align="right")
        items.append(hum)
    rows.append({"items": items})
    return True


def read_token() -> str:
    token = os.environ.get("HASS_TOKEN") or os.environ.get("HASSIO_TOKEN")
    if token:
        return token.strip()
    if TOKEN_FILE.exists():
        text = TOKEN_FILE.read_text(encoding="utf-8").strip()
        if text:
            return text
    sys.exit(
        "Kein Token gefunden. Entweder HASS_TOKEN setzen oder das Token in "
        f"{TOKEN_FILE} ablegen (Profil > Sicherheit > Langlebige Zugangstoken)."
    )


def call(method: str, url: str, token: str, payload: dict | None = None) -> dict:
    data = None
    headers = {"Authorization": f"Bearer {token}", "User-Agent": "okhttp/4.12.0"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as err:
        detail = err.read().decode("utf-8", "replace")[:400]
        sys.exit(f"{method} {url} -> HTTP {err.code}: {detail}")
    except urllib.error.URLError as err:
        sys.exit(f"{method} {url} nicht erreichbar: {err.reason}")

    return json.loads(body) if body else {}


def patch_values(widget: dict) -> bool:
    """Werte-Liste pflegen: Innenwerte ersetzen, Zeilen-Sensoren aufnehmen.

    Die App zieht die Sensorauswahl im Zeilen-Editor aus ``values`` – fehlt der
    Eintrag dort, bleibt der Abschnitt „Werte“ leer (auch wenn die Zeile den
    Sensor schon zeigt).
    """
    values: list[dict] = []
    seen: set[str] = set()
    changed = False

    def push(entity: str, label, color) -> None:
        nonlocal changed
        entry = {"entity": entity, "label": label or None, "color": color or None}
        values.append(entry)
        seen.add(entity)

    # 1) Bestehende Werte übernehmen, Innen-Sensoren auf Außen umstellen
    for current in widget.get("values") or []:
        entity = str(current.get("entity") or "")
        if not entity:
            continue
        label = current.get("label")
        color = current.get("color")
        kind = kind_of(entity)
        if kind == "temp":
            entity, label, color = TEMP_ENTITY, TEMP_LABEL, COLOR
        elif kind == "hum":
            entity, label, color = HUM_ENTITY, HUM_LABEL, COLOR
        if entity in seen:
            changed = True
            continue
        if entity != str(current.get("entity") or "") or label != current.get("label"):
            changed = True
        if entity != str(current.get("entity") or ""):
            color = COLOR
        push(entity, label, color)

    # 2) Außenwerte sicherstellen (Beschriftung/Farbe)
    by_entity = {entry["entity"]: entry for entry in values}
    for label, entity, color in OUTSIDE:
        entry = by_entity.get(entity)
        if entry is None:
            push(entity, label, color)
            by_entity[entity] = values[-1]
            changed = True
        elif entry.get("label") != label or entry.get("color") != color:
            entry["label"] = label
            entry["color"] = color
            changed = True

    # 3) Alle Sensoren aus den Zeilen aufnehmen (sonst fehlen sie in „Werte“)
    for row in widget.get("rows") or []:
        for item in row.get("items") or []:
            if item.get("type") != "sensor":
                continue
            entity = str(item.get("entity") or "")
            if not entity or entity in seen:
                continue
            label = str(item.get("label") or "").strip() or None
            color = str(item.get("color") or "").strip() or None
            push(entity, label, color)
            changed = True

    if changed or list(widget.get("values") or []) != values:
        widget["values"] = values
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default=DEFAULT_URL, help="HA-URL (Standard: %(default)s)")
    parser.add_argument("--dry-run", action="store_true", help="nichts speichern")
    parser.add_argument(
        "--all",
        action="store_true",
        help="auch Uhr-Fassungen (target=watch) ändern, nicht nur Handy-Widgets",
    )
    args = parser.parse_args()

    base = args.base.rstrip("/")
    token = read_token()
    widgets = call("GET", base + API, token).get("widgets") or []
    if not widgets:
        print("Keine Widgets gefunden – ist ha_widget_bridge eingerichtet?")
        return 1

    pending = 0
    for widget in widgets:
        target = widget.get("target") or "phone"
        name = f"{widget.get('id')} ({widget.get('name')}, {target})"
        if target != "phone" and not args.all:
            print(f"–  {name}: übersprungen (Uhr-Fassung, --all für alle)")
            continue
        rows_changed = patch_rows(widget)
        values_changed = patch_values(widget)
        if not (rows_changed or values_changed):
            print(f"=  {name}: Außenwerte schon vorhanden")
            continue
        pending += 1
        if args.dry_run:
            print(f"~  {name}: würde Außenwerte eintragen")
            continue
        call("POST", base + API, token, widget)
        print(f"+  {name}: Außenwerte eingetragen")

    if args.dry_run:
        print(f"\nTrockenlauf: {pending} Widget(s) würden geändert.")
    else:
        print(f"\nFertig: {pending} Widget(s) geändert.")
        print("Am Handy das Widget antippen/aktualisieren, damit es neu lädt.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
