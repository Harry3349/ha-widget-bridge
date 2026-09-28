#!/usr/bin/env python3
"""Erzeugt die Markensymbole der Integration (brand/icon.png + icon@2x.png).

Home Assistant und HACS zeigen für die Integration das Bild unter
``custom_components/<domain>/brand/icon.png``. Das Symbol ist bewusst eine
Eigenschöpfung (dunkle Kachel + grünes Power-Zeichen) und passt zur
Android-App (gleiche Farben: #101018 / #00E676).

Aufruf:  python3 tools/make_brand_icon.py
Benötigt: Pillow (pip install pillow)
"""

from __future__ import annotations

import pathlib
import sys

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover
    print("Pillow fehlt – bitte installieren: pip install pillow")
    sys.exit(1)

TARGET_DIR = (
    pathlib.Path(__file__).resolve().parents[1]
    / "custom_components"
    / "ha_widget_bridge"
    / "brand"
)

BACKGROUND = (16, 16, 24, 255)      # #101018
ACCENT = (0, 230, 118, 255)         # #00E676
SUPERSAMPLE = 4                     # Kantenglättung durch Überabtastung


def draw_symbol(image: Image.Image, color: tuple[int, int, int, int]) -> None:
    """Power-Zeichen (Ring mit Lücke oben + vertikaler Balken) aufziehen.

    Der Ring wird als Maske gezeichnet (Außenkreis minus Innenkreis minus
    Keil), damit die Kanten sauber sind und keine Naht zwischen zwei
    Kreisbögen entsteht.
    """
    size = float(image.width)
    center = size / 2
    radius = size * 0.225
    stroke = size * 0.076

    mask = Image.new("L", (int(size), int(size)), 0)
    mask_draw = ImageDraw.Draw(mask)

    outer_radius = radius + stroke / 2
    inner_radius = radius - stroke / 2
    mask_draw.ellipse(
        [center - outer_radius, center - outer_radius,
         center + outer_radius, center + outer_radius],
        fill=255,
    )
    mask_draw.ellipse(
        [center - inner_radius, center - inner_radius,
         center + inner_radius, center + inner_radius],
        fill=0,
    )

    # Keil oben ausstanzen: Lücke von je 45° links und rechts der 12-Uhr-Position
    reach = outer_radius * 2.0
    mask_draw.polygon(
        [(center, center), (center - reach, center - reach), (center + reach, center - reach)],
        fill=0,
    )

    # Vertikaler Balken: ragt oben über den Ring hinaus und endet im Ringinneren
    bar_top = center - outer_radius * 1.06
    bar_bottom = center + radius * 0.15
    mask_draw.rounded_rectangle(
        [center - stroke / 2, bar_top, center + stroke / 2, bar_bottom],
        radius=stroke / 2,
        fill=255,
    )

    image.paste(color, mask=mask)


def render_icon(pixel_size: int, rounded: bool) -> Image.Image:
    """Eine quadratische Kachel in der gewünschten Pixelgröße rendern."""
    work = pixel_size * SUPERSAMPLE
    image = Image.new("RGBA", (work, work), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    if rounded:
        draw.rounded_rectangle(
            [0, 0, work - 1, work - 1],
            radius=work * 0.22,
            fill=BACKGROUND,
        )

    draw_symbol(image, ACCENT)

    return image.resize((pixel_size, pixel_size), Image.LANCZOS)


def main() -> int:
    TARGET_DIR.mkdir(parents=True, exist_ok=True)

    outputs = {
        "icon.png": render_icon(256, rounded=True),
        "icon@2x.png": render_icon(512, rounded=True),
        # Für dunkle Hintergründe ohne eigene Kachel (transparentes Quadrat)
        "dark_icon.png": render_icon(256, rounded=False),
        "dark_icon@2x.png": render_icon(512, rounded=False),
    }

    for name, image in outputs.items():
        path = TARGET_DIR / name
        image.save(path, format="PNG", optimize=True)
        print(f"{path.relative_to(path.parents[3])}  {image.size[0]}x{image.size[1]}  "
              f"{path.stat().st_size} Bytes")

    return 0


if __name__ == "__main__":
    sys.exit(main())
