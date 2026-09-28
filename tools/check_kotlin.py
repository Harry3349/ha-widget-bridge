#!/usr/bin/env python3
"""Einfache Syntax-Plausibilitätsprüfung für Kotlin-Dateien (ohne Compiler).

Entfernt Kommentare, Strings (inkl. Raw-Strings) und Zeichen-Literale und prüft
danach die Balance von ``{}``, ``()`` und ``[]``. Genau diese Art Fehler
(zu viele oder zu wenige Klammern) meldet der Kotlin-Compiler als
„Syntax error“ an ganz anderer Stelle – deshalb lohnt der Vorab-Check.

Aufruf:  python3 tools/check_kotlin.py
"""

from __future__ import annotations

import glob
import sys

PAIRS = {"}": "{", ")": "(", "]": "["}
OPENING = set(PAIRS.values())


def strip_noise(text: str) -> str:
    """Kommentare, Strings und Zeichen-Literale durch Leerzeichen ersetzen."""
    out: list[str] = []
    index = 0
    length = len(text)

    while index < length:
        char = text[index]
        following = text[index + 1] if index + 1 < length else ""

        # Zeilenkommentar
        if char == "/" and following == "/":
            while index < length and text[index] != "\n":
                index += 1
            continue

        # Blockkommentar
        if char == "/" and following == "*":
            index += 2
            while index < length and not (text[index] == "*" and index + 1 < length and text[index + 1] == "/"):
                index += 1
            index += 2
            continue

        # Raw-String """...""" (auch mehrzeilig)
        if text[index : index + 3] == '"""':
            index += 3
            while index < length and text[index : index + 3] != '"""':
                index += 1
            index += 3
            continue

        # Normaler String "..."
        if char == '"':
            index += 1
            while index < length:
                if text[index] == "\\":
                    index += 2
                    continue
                if text[index] == '"' or text[index] == "\n":
                    index += 1
                    break
                index += 1
            continue

        # Zeichen-Literal 'x'
        if char == "'":
            index += 1
            while index < length:
                if text[index] == "\\":
                    index += 2
                    continue
                if text[index] == "'" or text[index] == "\n":
                    index += 1
                    break
                index += 1
            continue

        out.append(char)
        index += 1

    return "".join(out)


def check(path: str) -> list[str]:
    with open(path, encoding="utf-8") as handle:
        raw = handle.read()

    code = strip_noise(raw)
    line_of = [0] * len(code)
    line = 1
    for position, char in enumerate(code):
        line_of[position] = line
        if char == "\n":
            line += 1

    problems: list[str] = []
    stacks: dict[str, list[tuple[str, int]]] = {brace: [] for brace in OPENING}

    for position, char in enumerate(code):
        if char in OPENING:
            stacks[char].append((char, line_of[position]))
        elif char in PAIRS:
            opening = PAIRS[char]
            stack = stacks[opening]
            if not stack:
                problems.append(f"Zeile {line_of[position]}: '{char}' ohne passendes '{opening}'")
            else:
                stack.pop()

    for opening, stack in stacks.items():
        for _char, line_number in stack:
            problems.append(f"Zeile {line_number}: '{opening}' bleibt ungeschlossen")

    return problems


def main() -> int:
    files = sorted(glob.glob("android/app/src/**/*.kt", recursive=True))
    if not files:
        print("Keine Kotlin-Dateien gefunden.")
        return 1

    total = 0
    for path in files:
        problems = check(path)
        if problems:
            total += len(problems)
            print(f"\n{path}")
            for problem in problems:
                print(f"  {problem}")

    if total:
        print(f"\n{total} Problem(e) gefunden.")
        return 1

    print(f"OK: {len(files)} Kotlin-Dateien – Klammern sind ausgeglichen.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
