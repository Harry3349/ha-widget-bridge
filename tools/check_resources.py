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
MODULES = ("android/app/src/main", "android/wear/src/main")
RES = f"{ANDROID}/res"

KINDS = (
    "drawable", "layout", "mipmap", "xml", "string", "color", "style", "id", "array", "bool",
)


def collect(module: str) -> dict[str, set[str]]:
    """Alle Ressourcen eines Moduls einsammeln (jedes Modul hat sein eigenes R)."""
    res = f"{module}/res"
    defined: dict[str, set[str]] = {kind: set() for kind in KINDS}

    # 1) Definitionsdateien: res/<typ>[-*]/<name>.<ext>
    for path in glob.glob(f"{res}/*/*"):
        kind = path.split("/")[-2].split("-")[0]
        name = path.split("/")[-1].rsplit(".", 1)[0]
        defined.setdefault(kind, set()).add(name)

    # 2) Werte-Ressourcen: <string name=...>, <color name=...>, <style name=...>
    for path in glob.glob(f"{res}/values*/*.xml"):
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
    for path in glob.glob(f"{res}/layout/*.xml"):
        text = open(path, encoding="utf-8").read()
        for match in re.finditer(r'@\+id/([A-Za-z0-9_]+)', text):
            defined["id"].add(match.group(1))

    # 4) Style-Vererbung ("Theme.A.B" -> "Theme.A")
    for name in list(defined.get("style", ())):
        parts = name.split(".")
        for index in range(1, len(parts)):
            defined["style"].add(".".join(parts[:index]))

    return defined


def check(module: str, defined: dict[str, set[str]]) -> list[str]:
    """Verweise eines Moduls gegen die eigenen Ressourcen prüfen."""
    problems: list[str] = []

    for path in sorted(glob.glob(f"{module}/**/*.kt", recursive=True)):
        text = open(path, encoding="utf-8").read()
        for kind, name in re.findall(r"\bR\.([a-z_]+)\.([A-Za-z0-9_]+)", text):
            if kind not in defined:
                problems.append(f"{path}: unbekannter Ressourcentyp R.{kind}.{name}")
            elif name not in defined[kind]:
                problems.append(f"{path}: R.{kind}.{name} ist nicht definiert")

    for path in sorted(set(glob.glob(f"{module}/**/*.xml", recursive=True))):
        text = open(path, encoding="utf-8").read()
        # @string/foo, @drawable/foo, @xml/foo, @mipmap/foo, @color/foo, @style/foo
        for kind, name in re.findall(r'"@([a-z_]+)/([A-Za-z0-9_.]+)"', text):
            if kind in ("android", "id", "+id"):
                continue
            if kind not in defined:
                problems.append(f"{path}: unbekannter Ressourcentyp @{kind}/{name}")
            elif name not in defined[kind]:
                problems.append(f"{path}: @{kind}/{name} ist nicht definiert")

    return problems


problems: list[str] = []

definitions: dict[str, dict[str, set[str]]] = {}

for _module in MODULES:
    definitions[_module] = collect(_module)
    problems.extend(check(_module, definitions[_module]))

# --- Ergebnis -------------------------------------------------------------

if problems:
    print("\n".join(sorted(set(problems))))
    sys.exit(1)

print("OK: alle Ressourcen-Verweise sind auflösbar.")
for _module in MODULES:
    for kind in sorted(definitions[_module]):
        if definitions[_module][kind]:
            print(f"  {_module}: {kind}: {len(definitions[_module][kind])}")
