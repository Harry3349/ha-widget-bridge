#!/usr/bin/env python3
"""Prüft das Werte-Raster des Homescreen-Widgets gegen den Renderer.

RemoteViews kann keine Views zur Laufzeit erzeugen, deshalb sind die Werte-Felder
im Layout fest deklariert und werden vom Renderer per ``setViewVisibility``
ein-/ausgeblendet. Reihenfolge und Vollständigkeit der IDs sind damit reine
Konvention – genau das prüft dieses Skript:

* Sind die ID-Listen in ``WidgetRenderer.kt`` in der Reihenfolge des Layouts
  (zeilenweise, drei Felder pro Zeile)?
* Hat jedes Feld genau einen Namen- und einen Wert-TextView?
* Passen Rastergröße und die Grenzen aus der Integration zusammen
  (12 Werte, 1–3 Spalten)?
* Gibt es ungenutzte oder fehlende ``value_*``-IDs?

Aufruf:  python3 tools/check_widget_layout.py
"""

from __future__ import annotations

import math
import re
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
LAYOUT = "android/app/src/main/res/layout/widget_ha.xml"
STYLES = "android/app/src/main/res/values/styles.xml"
RENDERER = "android/app/src/main/java/de/reimann/hawidget/widget/WidgetRenderer.kt"
CONST = "custom_components/ha_widget_bridge/const.py"

problems: list[str] = []
checks = 0


def check(condition: bool, description: str) -> None:
    global checks
    checks += 1
    if not condition:
        problems.append(description)


def attr(element: ET.Element, name: str) -> str:
    return element.attrib.get(f"{{{ANDROID_NS}}}{name}", "")


def ref_name(value: str) -> str:
    """``@style/Foo``, ``@+id/foo`` oder ``android:layout_width`` → letzter Name."""
    return value.rsplit("/", 1)[-1].split(":", 1)[-1]


# --------------------------------------------------------------------------
# 0) Styles einlesen (Feldbreite/-gewicht steht im Style, nicht im Layout)
# --------------------------------------------------------------------------

style_defs: dict[str, dict[str, str]] = {}
try:
    style_root = ET.parse(STYLES).getroot()
except (OSError, ET.ParseError) as err:
    print(f"{STYLES} nicht lesbar: {err}")
    sys.exit(1)

for element in style_root:
    if element.tag != "style":
        continue
    name = element.attrib.get("name", "")
    items = {ref_name(item.attrib.get("name", "")): (item.text or "").strip() for item in element}
    items["__parent__"] = ref_name(element.attrib.get("parent", ""))
    style_defs[name] = items


def style_value(style: str, item: str) -> str:
    """Wert aus dem Style, notfalls vom Eltern-Style."""
    seen: set[str] = set()
    while style and style not in seen:
        seen.add(style)
        definition = style_defs.get(style)
        if definition is None:
            return ""
        if definition.get(item):
            return definition[item]
        style = definition.get("__parent__", "")
    return ""


# --------------------------------------------------------------------------
# 1) Layout einlesen: ID → Element
# --------------------------------------------------------------------------

try:
    root = ET.parse(LAYOUT).getroot()
except (OSError, ET.ParseError) as err:
    print(f"{LAYOUT} nicht lesbar: {err}")
    sys.exit(1)

elements: dict[str, ET.Element] = {}
duplicates: list[str] = []
for element in root.iter():
    name = ref_name(attr(element, "id"))
    if not name:
        continue
    if name in elements:
        duplicates.append(name)
    elements[name] = element

check(not duplicates, f"doppelte android:id im Layout: {sorted(set(duplicates))}")

# --------------------------------------------------------------------------
# 2) Renderer einlesen: Konstanten und ID-Listen
# --------------------------------------------------------------------------

renderer = open(RENDERER, encoding="utf-8").read()

constants = {
    key: int(value)
    for key, value in re.findall(
        r"private const val (MAX_VALUE_(?:ROWS|COLUMNS))\s*=\s*(\d+)", renderer
    )
}
check("MAX_VALUE_ROWS" in constants, "MAX_VALUE_ROWS fehlt in WidgetRenderer.kt")
check("MAX_VALUE_COLUMNS" in constants, "MAX_VALUE_COLUMNS fehlt in WidgetRenderer.kt")
if len(problems):
    print("\n".join(problems))
    sys.exit(1)

rows = constants["MAX_VALUE_ROWS"]
columns = constants["MAX_VALUE_COLUMNS"]

lists: dict[str, list[str]] = {}
for name, body in re.findall(
    r"private val (VALUE_\w+_IDS)\s*=\s*intArrayOf\(([^)]*)\)", renderer, re.S
):
    lists[name] = re.findall(r"R\.id\.(\w+)", body)

for expected in ("VALUE_ROW_IDS", "VALUE_CELL_IDS", "VALUE_NAME_IDS", "VALUE_TEXT_IDS"):
    check(expected in lists, f"{expected} fehlt in WidgetRenderer.kt")

if len(problems):
    print("\n".join(problems))
    sys.exit(1)

# --------------------------------------------------------------------------
# 3) Reihenfolge: Zeile für Zeile, drei Felder pro Zeile
# --------------------------------------------------------------------------

check(
    lists["VALUE_ROW_IDS"] == [f"value_row_{row}" for row in range(1, rows + 1)],
    "VALUE_ROW_IDS passt nicht zu value_row_1..N",
)

for slot in range(rows * columns):
    row, col = slot // columns + 1, slot % columns + 1
    check(
        lists["VALUE_CELL_IDS"][slot] == f"value_cell_{row}_{col}",
        f"VALUE_CELL_IDS[{slot}] sollte value_cell_{row}_{col} sein",
    )
    check(
        lists["VALUE_NAME_IDS"][slot] == f"value_name_{row}_{col}",
        f"VALUE_NAME_IDS[{slot}] sollte value_name_{row}_{col} sein",
    )
    check(
        lists["VALUE_TEXT_IDS"][slot] == f"value_text_{row}_{col}",
        f"VALUE_TEXT_IDS[{slot}] sollte value_text_{row}_{col} sein",
    )

# --------------------------------------------------------------------------
# 4) Layout: Felder, Kinder und Stile
# --------------------------------------------------------------------------

for slot in range(rows * columns):
    row, col = slot // columns + 1, slot % columns + 1
    cell = elements.get(f"value_cell_{row}_{col}")
    check(cell is not None, f"value_cell_{row}_{col} fehlt im Layout")
    if cell is None:
        continue

    children = [ref_name(attr(child, "id")) for child in cell if ref_name(attr(child, "id"))]
    check(
        children == [f"value_name_{row}_{col}", f"value_text_{row}_{col}"],
        f"value_cell_{row}_{col} hat die Kinder {children}",
    )

    style = ref_name(cell.attrib.get("style", ""))
    expected_style = "WidgetValueCell" if col == 1 else "WidgetValueCellGap"
    check(style == expected_style, f"value_cell_{row}_{col} nutzt {style} statt {expected_style}")
    check(
        style_value(style, "layout_width") == "0dp"
        and style_value(style, "layout_weight") == "1",
        f"{style} braucht layout_width=0dp und layout_weight=1 (gleich breite Felder)",
    )
    if col > 1:
        check(
            bool(style_value(style, "layout_marginLeft")),
            f"{style} braucht layout_marginLeft (Abstand zum linken Feld)",
        )

# Alle Raster-IDs im Layout müssen im Renderer vorkommen (und umgekehrt)
grid_id = re.compile(r"^value_(?:row|cell|name|text)_\d+")
layout_ids = {name for name in elements if grid_id.match(name)}
renderer_ids = {name for ids in lists.values() for name in ids}
check(
    layout_ids == renderer_ids,
    "Layout und Renderer nutzen unterschiedliche value_*-IDs: "
    f"nur im Layout {sorted(layout_ids - renderer_ids)}, "
    f"nur im Renderer {sorted(renderer_ids - layout_ids)}",
)

# --------------------------------------------------------------------------
# 5) Zusammenpassen mit den Grenzen aus der Integration
# --------------------------------------------------------------------------

const = open(CONST, encoding="utf-8").read()
max_values = int(re.search(r"^MAX_VALUES\s*=\s*(\d+)", const, re.M).group(1))
max_columns = int(re.search(r"^VALUE_COLUMNS_MAX\s*=\s*(\d+)", const, re.M).group(1))

check(
    columns >= max_columns,
    f"Raster hat {columns} Spalten, die Integration erlaubt aber {max_columns}",
)

# Ab zwei Spalten ordnet die App die Werte selbst an – dann muss das Raster
# für alle Werte reichen.
needed_rows = math.ceil(max_values / 2)
check(
    rows >= needed_rows,
    f"{max_values} Werte in 2 Spalten brauchen {needed_rows} Zeilen, das Raster hat {rows}",
)

# --------------------------------------------------------------------------
# 6) Füllung durchspielen (Indexrechnung des Renderers)
# --------------------------------------------------------------------------

for used_columns in range(2, max_columns + 1):
    for count in range(1, max_values + 1):
        slots = [
            index // used_columns * columns + index % used_columns
            for index in range(count)
        ]
        check(
            len(set(slots)) == count,
            f"{count} Werte in {used_columns} Spalten belegen ein Feld doppelt: {slots}",
        )
        check(
            all(slot < rows * columns for slot in slots),
            f"{count} Werte in {used_columns} Spalten passen nicht ins Raster",
        )
        check(
            max(slot // columns for slot in slots) < rows,
            f"{count} Werte in {used_columns} Spalten brauchen mehr als {rows} Zeilen",
        )

# --------------------------------------------------------------------------
# Ergebnis
# --------------------------------------------------------------------------

if problems:
    print(f"{len(problems)} von {checks} Prüfungen fehlgeschlagen:")
    for problem in problems:
        print(f"  - {problem}")
    sys.exit(1)

print(f"OK: Werte-Raster {rows}x{columns} passt zu Renderer und Layout ({checks} Prüfungen).")
