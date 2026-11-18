#!/usr/bin/env python3
"""Generate the SmartIQ launcher icon and in-app logo from the source artwork.

Requires Pillow (`pip install pillow`). Run from the repository root:

    python3 android/branding/generate-icons.py

The emblem is taken from `smartiq-logo.png` (emblem above the wordmark), with the
wifi marks on the right removed, then rendered at every density:

* `res/mipmap-*/ic_launcher_foreground.png` - 108dp adaptive-icon canvas
* `res/mipmap-*/ic_launcher.png`           - 48dp legacy square (white)
* `res/mipmap-*/ic_launcher_round.png`     - 48dp legacy round (white)
* `res/drawable-nodpi/smartiq_emblem.png`  - logo used in the sign-in headers
* `play-store-icon-512.png`, `icon-preview.png`
"""
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "android/branding"
RES = ROOT / "android/app/src/main/res"

# Wordmark and tagline start here; everything below belongs to the name, not the emblem.
WORDMARK_TOP = 774
# The wifi marks sit in the top-right corner region of the emblem.
WIFI_X, WIFI_Y = 1000, 640

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def extract_emblem(src: Image.Image) -> Image.Image:
    """Emblem = connected shapes above the wordmark, minus the wifi marks."""
    region = src.crop((0, 0, src.width, 900))
    step = 2
    small = region.getchannel("A").point(lambda v: 255 if v > 12 else 0).resize(
        (region.width // step, region.height // step), Image.BOX
    )
    w, h = small.size
    px = small.load()
    seen = [[False] * w for _ in range(h)]
    keep = Image.new("L", (w, h), 0)
    km = keep.load()

    for y in range(h):
        for x in range(w):
            if px[x, y] == 0 or seen[y][x]:
                continue
            queue = deque([(x, y)])
            seen[y][x] = True
            cells = []
            while queue:
                cx, cy = queue.popleft()
                cells.append((cx, cy))
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and px[nx, ny] > 0:
                        seen[ny][nx] = True
                        queue.append((nx, ny))
            x0 = min(c[0] for c in cells) * step
            y0 = min(c[1] for c in cells) * step
            if y0 >= WORDMARK_TOP or (x0 >= WIFI_X and y0 >= WIFI_Y) or len(cells) < 30:
                continue
            for cx, cy in cells:
                km[cx, cy] = 255

    mask = keep.resize(region.size, Image.BILINEAR).filter(ImageFilter.MaxFilter(3))
    cut = region.copy()
    cut.putalpha(Image.composite(region.getchannel("A"), Image.new("L", region.size, 0), mask))
    return cut.crop(cut.getchannel("A").getbbox())


def render(img: Image.Image, size: int, ratio: float, background=None, circle=False) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), background or (0, 0, 0, 0))
    scale = min(size * ratio / img.width, size * ratio / img.height)
    inner = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    canvas.alpha_composite(inner, ((size - inner.width) // 2, (size - inner.height) // 2))
    if circle:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        canvas.putalpha(mask)
    return canvas


def main() -> None:
    logo = Image.open(BRAND / "smartiq-logo.png").convert("RGBA")
    emblem = extract_emblem(logo)
    emblem.save(BRAND / "smartiq-emblem.png")
    emblem.resize((512, int(512 * emblem.height / emblem.width)), Image.LANCZOS).save(
        RES / "drawable-nodpi/smartiq_emblem.png"
    )

    for name, scale in DENSITIES.items():
        out = RES / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        # 74% keeps the artwork inside the 72dp adaptive safe circle with only the
        # outer book edges touching it.
        render(emblem, round(108 * scale), 0.74).save(out / "ic_launcher_foreground.png")
        render(emblem, round(48 * scale), 0.82, (255, 255, 255, 255)).save(out / "ic_launcher.png")
        render(emblem, round(48 * scale), 0.78, (255, 255, 255, 255), circle=True).save(
            out / "ic_launcher_round.png"
        )

    render(emblem, 512, 0.82, (255, 255, 255, 255)).save(BRAND / "play-store-icon-512.png")

    # contact sheet: adaptive (circle mask), legacy square, legacy round
    size = 300
    pieces = [
        # what a round launcher shows: the transparent foreground over the white background
        render(emblem, size, 0.74, (255, 255, 255, 255), circle=True),
        render(emblem, size, 0.82, (255, 255, 255, 255)),
        render(emblem, size, 0.78, (255, 255, 255, 255), circle=True),
    ]
    sheet = Image.new("RGBA", (len(pieces) * (size + 20) + 20, size + 40), (18, 38, 63, 255))
    for i, piece in enumerate(pieces):
        sheet.alpha_composite(piece, (20 + i * (size + 20), 20))
    sheet.convert("RGB").save(BRAND / "icon-preview.png")
    print(f"emblem {emblem.size} -> icons written for {', '.join(DENSITIES)}")


if __name__ == "__main__":
    main()
