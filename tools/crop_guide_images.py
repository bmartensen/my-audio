"""Crops raw Pixel 9 screenshots (1080 x 2424) to the part the user guide explains and scales them for the web.

Dialogs are found automatically: on the dimmed screen, their surface is the longest run of brighter rows near the
left edge. Full-screen views use fixed boxes. Status and navigation bars are always cut off. The home screen keeps
only its header, the first project (the demo) and the record button, so no other project names appear.

Usage: python tools/crop_guide_images.py <raw screenshot dir> <output dir>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

TOP = 150      # below the status bar
BOTTOM = 2293  # above the navigation bar
WIDTH = 540    # published width: half the phone's pixels, sharp on most screens

# Full-screen views: (top, bottom) in phone pixels.
BOXES = {
    'project': (TOP, BOTTOM),
    'project-record-track': (425, 1803),
    'effect-preview': (726, 1234),
    'envelope': (TOP, 2120),
    'notes': (170, 1815),
    'share': (TOP, 760),
    'settings': (TOP, 1622),
    'calibration': (TOP, 1520),
    'library': (TOP, 1300),
    'library-preview': (726, 1430),
}
# Dialogs over a dimmed screen: wide effect dialogs (screen width minus margins) and standard dialogs.
WIDE = (42, 1038, 82)      # dialog left, dialog right, column inside its padding
NARROW = (120, 958, 160)
DIALOGS = {'track-settings': WIDE, 'effect-reverb': WIDE, 'master': WIDE, 'effect-airwindows': WIDE,
           'metronome': NARROW, 'project-settings': NARROW, 'tuner': NARROW}
# Fixed (left, top, right, bottom): short warnings, also on top of another dialog where detection does not fit.
FIXED = {'live-warning': (158, 991, 921, 1479), 'monitor-warning': (158, 991, 921, 1479),
         'reset-dialog': (172, 991, 907, 1479)}


def luminance(pixel):
    r, g, b = pixel[:3]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def dialog_box(image, x):
    """Top and bottom of the dialog surface: the longest run of rows brighter than the dimmed background at [x]."""
    column = [luminance(image.getpixel((x, y))) for y in range(TOP, BOTTOM)]
    # The rows just below the status bar are always dimmed screen: dialogs never reach that high.
    background = max(column[:10])
    best, start = (0, 0), None
    for index, value in enumerate(column + [background]):
        if value > background + 6:
            if start is None:
                start = index
        elif start is not None:
            if index - start > best[1] - best[0]:
                best = (start, index)
            start = None
    if best[1] - best[0] < 200:
        raise ValueError('no dialog found')
    margin = 8  # the detection column crosses the rounded corners slightly inside
    return max(TOP, TOP + best[0] - margin), min(BOTTOM, TOP + best[1] + margin)


def rounded(image, radius=73):
    """The dialog with transparent corners, so no dimmed background shows around it."""
    mask = Image.new('L', image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1), radius, fill=255)
    result = image.convert('RGBA')
    result.putalpha(mask)
    return result


def scaled(image):
    height = round(image.height * WIDTH / image.width)
    return image.resize((WIDTH, height), Image.LANCZOS)


def main(source, target):
    source, target = Path(source), Path(target)
    target.mkdir(parents=True, exist_ok=True)
    for path in sorted(source.glob('*.png')):
        name = path.stem
        image = Image.open(path).convert('RGB')
        if name == 'home-full':
            top = image.crop((0, TOP, image.width, 535))
            bottom = image.crop((0, 1940, image.width, BOTTOM))
            joined = Image.new('RGB', (image.width, top.height + bottom.height), image.getpixel((5, 600)))
            joined.paste(top, (0, 0))
            joined.paste(bottom, (0, top.height))
            result, name = joined, 'home'
        elif name in BOXES:
            result = image.crop((0, *BOXES[name][:1], image.width, BOXES[name][1]))
        elif name in DIALOGS:
            left, right, column = DIALOGS[name]
            y0, y1 = dialog_box(image, column)
            result = rounded(image.crop((left, y0, right, y1)))
        elif name in FIXED:
            result = rounded(image.crop(FIXED[name]))
        else:
            print(f'skipped {path.name}')
            continue
        scaled(result).save(target / f'{name}.png', optimize=True)
        print(f'{name}.png {result.width}x{result.height}')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
