#!/usr/bin/env python3
"""Prüft ohne Android-SDK, ob alle Ressourcen-Verweise auflösbar sind.

* ``R.<typ>.<name>`` in Kotlin
* ``@<typ>/<name>`` in XML/Manifest/Layouts
* ``android:id="@+id/<name>"`` in Layouts (für ``R.id.<name>``)

Aufruf:  python3 tools/check_resources.py
"""

from __future__ import annotations

import glob
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "android/app/src/main"
RES = f"{ANDROID}/res"

defined: dict[str, set[str]] = {kind: set() for kind in (
    "drawable", "layout", "mipmap", "xml", "string", "color", "style", "id", "array", "bool",
)}

# 1) Definitionsdateien: res/<typ>[-*]/<name>.<ext>
for path in glob.glob(f"{RES}/*/*"):
    kind = path.split("/")[-2].split("-")[0]
    name = path.split("/")[-1].rsplit(".", 1)[0]
    defined.setdefault(kind, set()).add(name)

# 2) Werte-Ressourcen: <string name=...>, <color name=...>, <style name=...>
for path in glob.glob(f"{RES}/values*/*.xml"):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as err:
        print(f"XML-Fehler in {path}: {err}")
        sys.exit(1)
    for element in root:
        name = element.attrib.get("name")
        if name:
            defined.setdefault(element.tag, set()).add(name)

# 3) IDs aus Layouts
for path in glob.glob(f"{RES}/layout/*.xml"):
    text = open(path, encoding="utf-8").read()
    for match in re.finditer(r'@\+id/([A-Za-z0-9_]+)', text):
        defined["id"].add(match.group(1))

# 4) Style-Vererbung ("Theme.A.B" -> "Theme.A")
for name in list(defined.get("style", ())):
    parts = name.split(".")
    for index in range(1, len(parts)):
        defined["style"].add(".".join(parts[:index]))

# --- Verweise sammeln ------------------------------------------------------

problems: list[str] = []

for path in sorted(glob.glob(f"{ANDROID}/**/*.kt", recursive=True)):
    text = open(path, encoding="utf-8").read()
    for kind, name in re.findall(r"\bR\.([a-z_]+)\.([A-Za-z0-9_]+)", text):
        if kind not in defined:
            problems.append(f"{path}: unbekannter Ressourcentyp R.{kind}.{name}")
        elif name not in defined[kind]:
            problems.append(f"{path}: R.{kind}.{name} ist nicht definiert")

for path in sorted(set(glob.glob(f"{ANDROID}/**/*.xml", recursive=True))):
    text = open(path, encoding="utf-8").read()
    # @string/foo, @drawable/foo, @xml/foo, @mipmap/foo, @color/foo, @style/foo
    for kind, name in re.findall(r'"@([a-z_]+)/([A-Za-z0-9_.]+)"', text):
        if kind in ("android", "id", "+id"):
            continue
        if kind not in defined:
            problems.append(f"{path}: unbekannter Ressourcentyp @{kind}/{name}")
        elif name not in defined[kind]:
            problems.append(f"{path}: @{kind}/{name} ist nicht definiert")

# --- Ergebnis -------------------------------------------------------------

if problems:
    print("\n".join(sorted(set(problems))))
    sys.exit(1)

print("OK: alle Ressourcen-Verweise sind auflösbar.")
for kind in sorted(defined):
    if defined[kind]:
        print(f"  {kind}: {len(defined[kind])}")
