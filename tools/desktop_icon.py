"""Derives the desktop app icon from the approved 512 px store icon.

The store icon is a full square; on the Windows/Linux desktop it is shown as a rounded square with
a small transparent margin, like the icons around it. Writes the window icon (PNG) and the
multi-size Windows icon (ICO) for the installer. No image generation, only Pillow.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "branding" / "app-icon-512.png"
WINDOW_ICON = ROOT / "desktop" / "src" / "jvmMain" / "resources" / "icon.png"
INSTALLER_ICON = ROOT / "desktop" / "icons" / "my-audio.ico"

SIZE = 512
MARGIN = 0.04          # transparent border per side, relative to the size
CORNER_RADIUS = 0.22   # relative to the visible square
SUPERSAMPLING = 4      # anti-aliased corners


def rounded(icon: Image.Image) -> Image.Image:
    inset = round(SIZE * MARGIN)
    side = SIZE - 2 * inset
    body = icon.convert("RGBA").resize((side, side), Image.Resampling.LANCZOS)

    big = side * SUPERSAMPLING
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=round(big * CORNER_RADIUS), fill=255)
    mask = mask.resize((side, side), Image.Resampling.LANCZOS)

    body.putalpha(Image.composite(body.getchannel("A"), Image.new("L", (side, side), 0), mask))
    result = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    result.paste(body, (inset, inset), body)
    return result


def main():
    icon = rounded(Image.open(SOURCE))
    WINDOW_ICON.parent.mkdir(parents=True, exist_ok=True)
    icon.save(WINDOW_ICON, optimize=True)
    INSTALLER_ICON.parent.mkdir(parents=True, exist_ok=True)
    icon.save(INSTALLER_ICON, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print(f"{WINDOW_ICON.relative_to(ROOT)}\n{INSTALLER_ICON.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
