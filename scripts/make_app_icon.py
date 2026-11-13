"""Build the PrismLock launcher icon and README logo from the supplied render.

The source is a JPEG, so its "transparent" backdrop is a checkerboard baked into the
pixels (two greys). It is removed by flooding the low-saturation greys inwards from the
frame edge and then filling the enclosed holes, which keeps every white ridge of the
fingerprint. Outputs the legacy mipmaps, the adaptive foreground (safe zone) and the
README logo.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
DOCS = ROOT / 'docs'
DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/gpt-image-2_Create_a_completely_new_professional_logo_for_PrismLock_an_Android_LSPosed_modul-0.jpg')
DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
FOREGROUND = 432          # adaptive icon canvas
SAFE_ZONE = 0.66          # content must stay inside 66% of the adaptive canvas
LEGACY_PADDING = 0.06


def backdrop_mask(image):
    """True where the pixel belongs to the checkerboard that touches the frame edge."""
    grey = image.convert('RGB')
    red, green, blue = grey.split()
    maximum = ImageChops.lighter(ImageChops.lighter(red, green), blue)
    minimum = ImageChops.darker(ImageChops.darker(red, green), blue)
    light = minimum.point(lambda value: 255 if value >= 236 else 0)
    neutral = ImageChops.subtract(maximum, minimum).point(lambda value: 255 if value <= 5 else 0)
    candidate = ImageChops.darker(light, neutral)
    width, height = image.size
    filled = candidate.copy()
    for corner in ((0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1)):
        if filled.getpixel(corner):
            ImageDraw.floodfill(filled, corner, 128, thresh=0)
    return filled.point(lambda value: 255 if value == 128 else 0)


def cutout(source):
    image = source.convert('RGB')
    backdrop = backdrop_mask(image)
    art = image.convert('RGBA')
    art.putalpha(backdrop.point(lambda value: 0 if value else 255))
    bounds = art.getchannel('A').getbbox()
    return art.crop(bounds)


def square(art, size, ratio):
    """Logo scaled to `ratio` of a square canvas and centred, transparent elsewhere."""
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    inner = max(1, round(size * ratio))
    scaled = art.resize((inner, inner), Image.Resampling.LANCZOS)
    canvas.paste(scaled, ((size - inner) // 2, (size - inner) // 2), scaled)
    return canvas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', type=Path, default=DEFAULT_SOURCE)
    parser.add_argument('--debug', type=Path, help='write a contact sheet here')
    args = parser.parse_args()
    art = cutout(Image.open(args.source))
    for density, size in DENSITIES.items():
        path = RES / f'mipmap-{density}/ic_launcher.png'
        square(art, size, 1 - LEGACY_PADDING * 2).save(path)
    square(art, FOREGROUND, SAFE_ZONE).save(RES / 'drawable-nodpi/ic_launcher_foreground.png')
    branding = DOCS / 'branding'
    branding.mkdir(parents=True, exist_ok=True)
    mark = square(art, 512, 0.94)
    mark.save(branding / 'logo-transparent.png')
    solid = Image.new('RGBA', (512, 512), (11, 16, 32, 255))
    solid.alpha_composite(mark)
    solid.save(branding / 'logo.png')
    if args.debug:
        sheet = Image.new('RGB', (3 * 220, 220), (255, 255, 255))
        for index, background in enumerate(((255, 255, 255), (18, 18, 24), (0, 0, 0))):
            tile = Image.new('RGBA', (220, 220), background + (255,))
            tile.alpha_composite(square(art, 200, 0.94), (10, 10))
            sheet.paste(tile.convert('RGB'), (index * 220, 0))
        sheet.save(args.debug)
    print(f'logo {art.width}x{art.height} from {args.source.name}; mipmaps, adaptive foreground '
          f'and docs/branding/logo-transparent.png written')


if __name__ == '__main__':
    main()
