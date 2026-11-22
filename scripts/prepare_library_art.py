#!/usr/bin/env python3
"""Split the approved library illustrations into Android artwork (Pillow; not a build step).

The wall/wood and each selectable book are independent drawables. Coordinates are in
one 952-wide illustration; the same three centred slots are laid out by LibraryScreen.
"""
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / 'design'
OUT = ROOT / 'app/src/main/res/drawable-nodpi'
LANCZOS = Image.Resampling.LANCZOS
WIDTH, COVER_W, TOP = 952, 282, 15
# Physical slots in right-to-left catalogue order. English stands in the centre.
SLOTS = (631, 335, 39)
# (start of shelf, end of shelf, foot of cover, subjects); even shelf heights keep
# the background aspect exact when its resolution is halved.
ROWS = (
    (223, 675, 611, ('quran', 'faith', 'fiqh')),
    (675, 1119, 1055, ('hadith', 'seerah', 'grammar')),
    (1119, 1591, 1500, ('reading', 'literature', 'chemistry')),
    (1591, 2051, 1982, ('physics', 'biology', 'math')),
    (2051, 2569, 2423, ('english',)),
)


def clear_rectangle(image, box):
    """Continue the adjacent wall behind the removable artwork (not a black cut-out)."""
    x0, y0, x1, y1 = box
    size = (x1 - x0, y1 - y0)
    def side(x):
        return image.crop((x, y0, x + 1, y1)).resize(size)
    gradient = Image.linear_gradient('L').transpose(Image.Transpose.ROTATE_90).resize(size)
    image.paste(Image.composite(side(x1), side(x0 - 1), gradient), (x0, y0))


def masthead(source):
    original = source.crop((0, 0, WIDTH, 223))
    sky = original.copy()
    logo_box = (622, 20, 895, 69)
    for box in [(32, 51, 250, 150), (616, 16, 901, 74), (542, 82, 918, 221)]:
        clear_rectangle(sky, box)
    sky = sky.filter(ImageFilter.GaussianBlur(3))
    # Keep the cyan book and white brand lettering; all Arabic header text and the badge
    # will be native, font-scalable text rather than a screenshot with a baked-in count.
    logo = original.crop(logo_box).convert('RGBA')
    bg = sky.crop(logo_box)
    for y in range(logo.height):
        for x in range(logo.width):
            color, back = logo.getpixel((x, y))[:3], bg.getpixel((x, y))
            alpha = min(1., max(0., (color[1] - back[1]) / max(1, 245 - back[1])))
            if alpha < 0.055:
                logo.putpixel((x, y), (0, 0, 0, 0))
            else:
                rgb = tuple(max(0, min(255, round(b + (c - b) / alpha))) for c, b in zip(color, back))
                logo.putpixel((x, y), (*rgb, round(255 * alpha)))
    logo.save(OUT / 'library_wordmark.webp', lossless=True, method=6)
    sky.resize((476, 112), LANCZOS).save(OUT / 'library_header_sky.webp', quality=86, method=6)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    first = Image.open(DESIGN / 'library-shelves-preview.png').convert('RGB')
    last = Image.open(DESIGN / 'library-final-four.png').convert('RGB')  # approved neuron, not the discarded skeleton
    assert first.size == (952, 1652) and last.size == (1152, 1461)
    masthead(first)
    tail = last.crop((0, 267, last.width, 1454))
    tail = tail.resize((WIDTH, round(tail.height * WIDTH / tail.width)), LANCZOS)
    wall = Image.new('RGB', (WIDTH, 1589 + tail.height))
    wall.paste(first.crop((0, 0, WIDTH, 1605)))
    # Join only the empty wall between shelves 3 and 4, as in the approved long preview.
    fade = Image.linear_gradient('L').resize((WIDTH, 16))
    wall.paste(tail.crop((0, 0, WIDTH, 16)), (0, 1589), fade)
    wall.paste(tail.crop((0, 16, WIDTH, tail.height)), (0, 1605))
    for n, (start, end, feet, books) in enumerate(ROWS, 1):
        board = wall.crop((0, start, WIDTH, end))
        for x, name in zip(SLOTS if len(books) == 3 else (SLOTS[1],), books):
            box = (x, TOP, x + COVER_W, feet - start)
            board.crop(box).save(OUT / f'library_cover_{name}.webp', quality=93, method=6)
            clear_rectangle(board, box)
        # The cover lettering keeps its full resolution. Only the noninteractive texture is reduced.
        board.resize((WIDTH // 2, round(board.height / 2)), LANCZOS).save(
            OUT / f'library_shelf_{n}.webp', quality=87, method=6,
        )


if __name__ == '__main__':
    main()
