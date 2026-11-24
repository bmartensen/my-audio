"""Microsoft Store images of the Windows app from the preserved logo master; no image generation.

Writes to branding/microsoft-store/:
  listing/  images uploaded in Partner Center (app tile icon, 16:9 super hero art)
  package/  logos the MSIX package carries (taskbar/start icon sizes, tiles, store logo)

Usage:  .venv\\Scripts\\python.exe tools\\prepare_store_assets.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MASTER = ROOT / "assets/source/branding/my-audio-symbol-original.png"
OUTPUT = ROOT / "branding/microsoft-store"
# Same graphite gradient as the Android and Google Play icon (tools/prepare_branding.py).
TOP, BOTTOM = (39, 44, 51), (11, 14, 18)
SUPERSAMPLING = 4


def gradient(width, height):
    result = Image.new("RGBA", (width, height))
    draw = ImageDraw.Draw(result)
    for y in range(height):
        fraction = y / max(1, height - 1)
        colour = tuple(round(a + (b - a) * fraction) for a, b in zip(TOP, BOTTOM)) + (255,)
        draw.line((0, y, width, y), fill=colour)
    return result


def symbol():
    original = Image.open(MASTER).convert("RGBA")
    return original.crop(original.getchannel("A").getbbox())


def fit(image, width, height):
    scale = min(width / image.width, height / image.height)
    return image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale))), Image.Resampling.LANCZOS)


def place(canvas, image, box_width, box_height, centre_x, centre_y):
    scaled = fit(image, box_width, box_height)
    canvas.alpha_composite(scaled, (round(centre_x - scaled.width / 2), round(centre_y - scaled.height / 2)))


def rounded(size, mark, margin=0.04, radius=0.22, fill=0.84):
    """The desktop icon: the symbol on the gradient in a rounded square with a small transparent margin."""
    big = size * SUPERSAMPLING
    inset = round(big * margin)
    side = big - 2 * inset
    body = gradient(side, side)
    place(body, mark, round(side * fill), round(side * fill), side / 2, side / 2)
    mask = Image.new("L", (side, side), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, side - 1, side - 1), radius=round(side * radius), fill=255)
    body.putalpha(mask)
    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    canvas.alpha_composite(body, (inset, inset))
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def square(size, mark, fill=0.84):
    """Full square with the gradient, like the store icon of Google Play."""
    canvas = gradient(size, size)
    place(canvas, mark, round(size * fill), round(size * fill), size / 2, size / 2)
    return canvas


def tile(width, height, mark, fill=0.66):
    """A start-menu tile: the symbol alone on transparency, centred."""
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    place(canvas, mark, round(height * fill), round(height * fill), width / 2, height / 2)
    return canvas


def save(image, relative):
    path = OUTPUT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)
    return path


def framed(window_png, width=1920, height=1080, corner=8, top=110):
    """A window capture centred on the graphite gradient with a soft shadow, as a Store screenshot.

    Windows 11 rounds window corners, so the capture's corners show whatever lay behind; they are cut away.
    The window sits high, keeping the Store's overlay area in the lower third mostly free.
    """
    from PIL import ImageFilter

    window = Image.open(window_png).convert("RGBA")
    mask = Image.new("L", (window.width * SUPERSAMPLING, window.height * SUPERSAMPLING), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, mask.width - 1, mask.height - 1),
                                           radius=corner * SUPERSAMPLING, fill=255)
    window.putalpha(mask.resize(window.size, Image.Resampling.LANCZOS))
    canvas = gradient(width, height)
    left = (width - window.width) // 2
    shadow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((left, top + 18, left + window.width, top + 18 + window.height),
                                             radius=corner, fill=(0, 0, 0, 150))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(28)))
    canvas.alpha_composite(window, (left, top))
    return canvas.convert("RGB")


def screenshots(source_dir):
    """Frames the captures `<language>-<REGION>-<n>-<name>.png` in [source_dir] into screenshots/<language>-<REGION>/."""
    for capture in sorted(Path(source_dir).glob("*-*-*-*.png")):
        language, region, rest = capture.name.split("-", 2)
        save(framed(capture), f"screenshots/{language}-{region}/{rest}")


def main():
    import sys

    if len(sys.argv) == 3 and sys.argv[1] == "--screenshots":
        screenshots(sys.argv[2])
        return
    mark = symbol()

    # Store listing: the app tile icon (300 x 300) and the 16:9 super hero art without text or app UI,
    # its symbol above the lower third where the Store lays its overlays.
    save(square(300, mark), "listing/app-tile-icon-300.png")
    hero = gradient(1920, 1080)
    place(hero, mark, 620, 620, 1920 / 2, 1080 * 0.40)
    save(hero.convert("RGB"), "listing/super-hero-art-1920x1080.png")

    # Package logos (names as an MSIX manifest refers to them, scale and target-size variants).
    for scale, size in ((100, 44), (200, 88), (400, 176)):
        save(rounded(size, mark), f"package/Square44x44Logo.scale-{scale}.png")
    for size in (16, 24, 32, 48, 256):
        save(rounded(size, mark), f"package/Square44x44Logo.targetsize-{size}.png")
        save(rounded(size, mark), f"package/Square44x44Logo.targetsize-{size}_altform-unplated.png")
    for scale, size in ((100, 150), (200, 300)):
        save(tile(size, size, mark), f"package/Square150x150Logo.scale-{scale}.png")
    for scale, (width, height) in ((100, (310, 150)), (200, (620, 300))):
        save(tile(width, height, mark), f"package/Wide310x150Logo.scale-{scale}.png")
    for scale, size in ((100, 50), (200, 100), (400, 200)):
        save(rounded(size, mark), f"package/StoreLogo.scale-{scale}.png")


if __name__ == "__main__":
    main()
