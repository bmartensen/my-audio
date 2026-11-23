"""Crops raw Pixel 9 screenshots (1080 x 2424, landscape 2424 x 1080) to the part the user guide explains and
scales them for the web.

Dialogs are found automatically: on the dimmed screen, their surface is the longest run of brighter rows near the
left edge. Full-screen views use fixed boxes. Status and navigation bars and the landscape camera cutout are always
cut off. The portrait home screen keeps the header, the project list with the New project action and the record
button; the empty band between them is cut out, so the record button sits closer to the list.

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
    'project-record-track': (795, 1830),
    'effect-preview': (860, 1300),
    'envelope': (TOP, 2120),
    'notes': (170, 1840),
    'share': (TOP, 905),
    'settings': (TOP, 1622),
    'calibration': (TOP, 1520),
    'library': (TOP, 1570),
    'library-preview': (960, 1420),
}
# Landscape views: (left, top, right, bottom) between the camera cutout, status bar and navigation bar.
LANDSCAPE = (140, 80, 2290, 1080)
# Portrait home: (top, bottom) bands that are stacked: header and list with New project (five projects; each
# further project adds 189 px), then the record button.
HOME_BANDS = [(TOP, 1471), (1950, 2275)]
# Dialogs over a dimmed screen: wide effect dialogs (screen width minus margins) and standard dialogs.
WIDE = (42, 1038, 82)      # dialog left, dialog right, column inside its padding
NARROW = (120, 958, 160)
DIALOGS = {'track-settings': WIDE, 'effect-reverb': WIDE, 'master': WIDE, 'effect-airwindows': WIDE,
           'metronome': NARROW, 'project-settings': NARROW, 'tuner': NARROW}
# Fixed (left, top, right, bottom): short warnings, also on top of another dialog where detection does not fit.
FIXED = {'live-warning': (151, 988, 929, 1482)}


def luminance(pixel):
    r, g, b = pixel[:3]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def dialog_box(image, x):
    """Top and bottom of the dialog surface: the longest run of rows at least as bright as the surface at [x].

    The surface is the most frequent brightness in the column; dimmed content behind the dialog, such as the
    calibration notice, stays darker and is therefore excluded even where it touches the dialog.
    """
    column = [round(luminance(image.getpixel((x, y)))) for y in range(TOP, BOTTOM)]
    surface = max(set(column), key=column.count)
    background = 0
    best, start = (0, 0), None
    for index, value in enumerate(column + [background]):
        if value >= surface - 3:
            if start is None:
                start = index
        elif start is not None:
            if index - start > best[1] - best[0]:
                best = (start, index)
            start = None
    if best[1] - best[0] < 200:
        raise ValueError('no dialog found')
    margin = 8  # the detection column crosses the rounded corners slightly inside
    top, bottom = max(TOP, TOP + best[0] - margin), min(BOTTOM, TOP + best[1] + margin)
    # The straight top and bottom edges are crossed in the middle: drop margin rows that are not dialog surface.
    centre = image.width // 2
    while top < bottom and round(luminance(image.getpixel((centre, top)))) < surface - 3:
        top += 1
    while bottom > top and round(luminance(image.getpixel((centre, bottom - 1)))) < surface - 3:
        bottom -= 1
    return top, bottom


def rounded(image, radius=73):
    """The dialog with transparent corners, so no dimmed background shows around it."""
    mask = Image.new('L', image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1), radius, fill=255)
    result = image.convert('RGBA')
    result.putalpha(mask)
    return result


def scaled(image, width=WIDTH):
    height = round(image.height * width / image.width)
    return image.resize((width, height), Image.LANCZOS)


def main(source, target):
    source, target = Path(source), Path(target)
    target.mkdir(parents=True, exist_ok=True)
    for path in sorted(source.glob('*.png')):
        name = path.stem
        image = Image.open(path).convert('RGB')
        if name == 'home':
            bands = [image.crop((0, top, image.width, bottom)) for top, bottom in HOME_BANDS]
            result = Image.new('RGB', (image.width, sum(band.height for band in bands)))
            y = 0
            for band in bands:
                result.paste(band, (0, y))
                y += band.height
        elif name == 'project-landscape':
            result = image.crop(LANDSCAPE)
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
        # Landscape images keep half the phone's pixels as well, so text matches the portrait images.
        width = result.width // 2 if result.width > image.height else WIDTH
        scaled(result, width).save(target / f'{name}.png', optimize=True)
        print(f'{name}.png {result.width}x{result.height}')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
