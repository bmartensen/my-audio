"""Prepare Android/Google Play sizes from the preserved masters; no API calls.

Run with the repository's Pillow environment: .venv/Scripts/python.exe tools/generate_icon.py
"""
from pathlib import Path
import math
import shutil
from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "assets/source/branding"
OUTPUT = ROOT / "branding"
RES = ROOT / "app/src/main/res"
TOP, BOTTOM = (39, 44, 51), (11, 14, 18)


def background(size):
    result = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(result)
    for y in range(size):
        fraction = y / (size - 1)
        colour = tuple(round(a + (b - a) * fraction) for a, b in zip(TOP, BOTTOM)) + (255,)
        draw.line((0, y, size, y), fill=colour)
    return result


def place(canvas, symbol, height):
    width = round(symbol.width * height / symbol.height)
    scaled = symbol.resize((width, height), Image.Resampling.LANCZOS)
    canvas.alpha_composite(scaled, ((canvas.width - width) // 2, (canvas.height - height) // 2))


def main():
    OUTPUT.mkdir(exist_ok=True)
    drawable = RES / "drawable"
    bitmap_dir = RES / "drawable-nodpi"
    bitmap_dir.mkdir(exist_ok=True)
    original = Image.open(SOURCE / "my-audio-symbol-original.png").convert("RGBA")
    alpha = original.getchannel("A")
    assert alpha.getextrema() == (0, 255), "The master must have real transparency"
    # Crop only the empty margin; keep the original master untouched.
    symbol = original.crop(alpha.getbbox())
    store = background(512)
    place(store, symbol, 432)
    store_path = OUTPUT / "app-icon-512.png"
    store.save(store_path, optimize=True)
    assert store_path.stat().st_size <= 1024 * 1024
    shutil.copyfile(store_path, ROOT / "app/src/main/ic_launcher-playstore.png")

    # Fit the actual alpha silhouette into Android's central 66 dp safe circle at 4 px/dp.
    # Using the bounding rectangle alone would clip the wide keyboard corners under round masks.
    alpha = symbol.getchannel("A")
    cx, cy = symbol.width / 2, symbol.height / 2
    radius = math.sqrt(max((x - cx) ** 2 + (y - cy) ** 2
        for y in range(symbol.height) for x in range(symbol.width) if alpha.getpixel((x, y)) > 0))
    height = math.floor(symbol.height * 128 / radius)
    foreground = Image.new("RGBA", (432, 432))
    place(foreground, symbol, height)
    foreground.save(bitmap_dir / "ic_launcher_brand.png", optimize=True)
    (drawable / "ic_launcher_foreground.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<!-- Prepared by tools/generate_icon.py from assets/source/branding/. -->
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ic_launcher_brand" android:gravity="fill" android:filter="true" />
''', encoding="utf-8")
    (drawable / "ic_launcher_background.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<!-- Graphite backdrop, prepared by tools/generate_icon.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0"
                android:endX="54" android:endY="108" android:startColor="#272C33" android:endColor="#0B0E12" />
        </aapt:attr>
    </path>
</vector>
''', encoding="utf-8")

    for language in ("de", "en"):
        source = Image.open(SOURCE / f"my-audio-feature-{language}-original.png")
        feature = ImageOps.fit(source.convert("RGB"), (1024, 500), method=Image.Resampling.LANCZOS)
        feature.save(OUTPUT / f"feature-graphic-{language}-1024x500.png", optimize=True)

    # Visual QA: actual circular launcher mask at normal and enlarged sizes, plus both banners.
    launcher = background(432)
    launcher.alpha_composite(foreground)
    launcher = launcher.crop((72, 72, 360, 360))
    circle = Image.new("L", launcher.size)
    ImageDraw.Draw(circle).ellipse((0, 0, 287, 287), fill=255)
    launcher.putalpha(circle)
    overview = Image.new("RGB", (1024, 1360), (18, 21, 26))
    overview.paste(store.resize((288, 288), Image.Resampling.LANCZOS), (160, 36))
    overview.paste(launcher.resize((192, 192), Image.Resampling.LANCZOS), (560, 60),
                   launcher.getchannel("A").resize((192, 192), Image.Resampling.LANCZOS))
    for size, x in ((48, 570), (64, 650)):
        small = launcher.resize((size, size), Image.Resampling.LANCZOS)
        overview.paste(small, (x, 270), small)
    for language, y in (("de", 360), ("en", 860)):
        overview.paste(Image.open(OUTPUT / f"feature-graphic-{language}-1024x500.png"), (0, y))
    overview.save(OUTPUT / "branding-overview.png", optimize=True)
    print(f"Prepared Play icon ({store_path.stat().st_size} bytes), safe adaptive foreground, DE/EN banners and overview.")


if __name__ == "__main__":
    main()
