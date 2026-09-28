#!/usr/bin/env python3
"""Prüft ohne Android-SDK die statischen Dateien des Projekts.

* JSON (manifest.json, hacs.json, strings.json, translations, Vorlagen)
* XML (Android-Ressourcen: Layouts, Drawables, Manifest, AppWidget-Info)
* YAML (GitHub-Workflow), sofern PyYAML installiert ist

Aufruf:  python3 tools/check.py
"""

from __future__ import annotations

import glob
import json
import sys
import xml.etree.ElementTree as ET

SKIP = ("/build/", "/.gradle/", "/.git/")

errors: list[str] = []


def relevant(path: str) -> bool:
    return not any(part in path for part in SKIP)


for file in sorted(glob.glob("**/*.json", recursive=True)):
    if not relevant(file):
        continue
    try:
        with open(file, encoding="utf-8") as handle:
            json.load(handle)
    except Exception as err:  # noqa: BLE001
        errors.append(f"JSON  {file}: {err}")

for file in sorted(glob.glob("**/*.xml", recursive=True)):
    if not relevant(file):
        continue
    try:
        ET.parse(file)
    except Exception as err:  # noqa: BLE001
        errors.append(f"XML   {file}: {err}")

try:
    import yaml
except ImportError:
    print("Hinweis: PyYAML ist nicht installiert – YAML wird nicht geprüft.")
else:
    for pattern in ("**/*.yml", "**/*.yaml"):
        for file in sorted(glob.glob(pattern, recursive=True)):
            if not relevant(file):
                continue
            try:
                with open(file, encoding="utf-8") as handle:
                    yaml.safe_load(handle)
            except Exception as err:  # noqa: BLE001
                errors.append(f"YAML  {file}: {err}")

if errors:
    print("\n".join(errors))
    sys.exit(1)

print("OK: alle JSON/XML/YAML-Dateien sind syntaktisch gültig.")
