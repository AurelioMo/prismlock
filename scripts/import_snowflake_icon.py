"""Create the Snowflake fingerprint icon from the supplied frost render.

The render sits on a dark backdrop with a soft bloom, so the alpha comes from a
border flood fill on a brightness threshold: the backdrop is removed completely (no
haze left behind the icon) while every enclosed dark leaf stays opaque. The silhouette
is then fitted to the same 216px footprint the other photo icons use.
"""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/assets/catalog'
PREVIEWS = ROOT / 'app/src/main/assets/previews'
DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/6d3e6dae6d49afdd2f210bef81f24b3f.jpg')
TAG = 'snowflake'
CANVAS = 288
CONTENT = 216
ALPHA_THRESHOLD = 16
BACKDROP = 80      # below this the pixel can belong to the backdrop
FEATHER = 1.2
# Scale by this fraction of the silhouette so the artwork reads as large as the
# glyph icons: catalogBounds, not the canvas, decides the rendered size.
BOUNDS_FIT = 0.9


def silhouette(image):
    """Opaque artwork with a transparent, edge-connected backdrop."""
    candidate = image.convert('L').point(lambda value: 255 if value < BACKDROP else 0)
    filled = candidate.copy()
    for corner in ((0, 0), (candidate.width - 1, 0), (0, candidate.height - 1),
                   (candidate.width - 1, candidate.height - 1)):
        if filled.getpixel(corner) == 255:
            ImageDraw.floodfill(filled, corner, 128, thresh=0)
    mask = filled.point(lambda value: 0 if value == 128 else 255)
    # Close pinholes between the ice branches without eroding the silhouette.
    mask = mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(5))
    return mask.filter(ImageFilter.GaussianBlur(FEATHER))


def png(image):
    buffer = io.BytesIO()
    image.save(buffer, format='PNG', optimize=True)
    return buffer.getvalue()


def document(art, name):
    bounds = art.getchannel('A').point(lambda alpha: 255 if alpha >= ALPHA_THRESHOLD else 0).getbbox()
    mid_x, mid_y = (bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2
    halves = [(bound - (mid_x if horizontal else mid_y)) * BOUNDS_FIT
              for horizontal, bound in ((True, bounds[0]), (False, bounds[1]), (True, bounds[2]), (False, bounds[3]))]
    centered = [mid_x + halves[0] - CANVAS / 2, mid_y + halves[1] - CANVAS / 2,
                mid_x + halves[2] - CANVAS / 2, mid_y + halves[3] - CANVAS / 2]
    asset = 'frame_0'
    return {'v': '5.9.1', 'fr': 120, 'ip': 0, 'op': 90, 'w': CANVAS, 'h': CANVAS, 'nm': name, 'ddd': 0,
            'assets': [{'id': asset, 'w': art.width, 'h': art.height, 'u': '', 'e': 1,
                        'p': 'data:image/png;base64,' + base64.b64encode(png(art)).decode()}],
            'layers': [{'ddd': 0, 'ind': 1, 'ty': 2, 'nm': asset, 'refId': asset, 'sr': 1,
                        'ip': 0, 'op': 90, 'st': 0, 'bm': 0,
                        'ks': {'o': {'a': 0, 'k': 100}, 'r': {'a': 0, 'k': 0},
                               'p': {'a': 0, 'k': [CANVAS / 2, CANVAS / 2, 0]},
                               'a': {'a': 0, 'k': [art.width / 2, art.height / 2, 0]},
                               's': {'a': 0, 'k': [100, 100, 100]}}}],
            'markers': [], 'catalogBounds': centered, 'catalogAlphaThreshold': ALPHA_THRESHOLD,
            'catalogSourceFps': 120, 'catalogFrameStep': 1}


def preview(art, bounds):
    """Thumbnail in the shared 112-unit preview footprint on a 144px tile."""
    scale = 112 / max(bounds[2] - bounds[0], bounds[3] - bounds[1])
    center_x = art.width / 2 + (bounds[0] + bounds[2]) / 2
    center_y = art.height / 2 + (bounds[1] + bounds[3]) / 2
    return art.transform((144, 144), Image.Transform.AFFINE,
                         (1 / scale, 0, center_x - 72 / scale, 0, 1 / scale, center_y - 72 / scale),
                         resample=Image.Resampling.BICUBIC)


def recolored(art, value):
    """Light/AOD variants keep the alpha and swap the colour, as the other icons do."""
    variant = Image.new('RGBA', art.size, (value, value, value, 255))
    variant.putalpha(art.getchannel('A'))
    return variant


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', type=Path, default=DEFAULT_SOURCE)
    parser.add_argument('--debug', type=Path, help='write a proof sheet here')
    args = parser.parse_args()
    source = Image.open(args.source).convert('RGB')
    mask = silhouette(source)
    art = source.convert('RGBA')
    art.putalpha(mask)
    art = art.crop(mask.getbbox())
    # Fit inside the shared footprint without distortion, then centre it.
    scale = CONTENT / max(art.width, art.height)
    art = art.resize((max(1, round(art.width * scale)), max(1, round(art.height * scale))),
                     Image.Resampling.LANCZOS)
    canvas = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.paste(art, ((CANVAS - art.width) // 2, (CANVAS - art.height) // 2))
    base = document(canvas, 'Snowflake')
    base['source_sha256'] = hashlib.sha256(args.source.read_bytes()).hexdigest()
    # Colour-only icon: a black/white recolor of a photo turns into a solid blob.
    (CATALOG / f'icons_{TAG}.json').write_text(json.dumps(base, separators=(',', ':')))
    for stale in (CATALOG / f'icons_{TAG}_light.json', CATALOG / f'icons_{TAG}_aod.json'):
        if stale.exists():
            stale.unlink()
    thumbnail = preview(canvas, base['catalogBounds'])
    (PREVIEWS / f'icons_{TAG}.png').write_bytes(png(thumbnail))
    index_path = CATALOG / 'index.json'
    index = json.loads(index_path.read_text())
    index['icons'] = [entry for entry in index['icons'] if entry['tag'] != TAG]
    index['icons'].append({'tag': TAG, 'file': f'icons_{TAG}.json', 'frames': 1,
                           'external_source': args.source.name,
                           'source_sha256': base['source_sha256'],
                           'style_label': 'Crystal Snowflake'})
    index_path.write_text(json.dumps(index, indent=2) + '\n')
    if args.debug:
        sheet = Image.new('RGB', (3 * 200, 200))
        tiles = ((canvas, (18, 18, 24)), (recolored(art, 0), (248, 248, 250)),
                 (recolored(art, 255), (18, 18, 24)))
        for index_x, (artwork, background) in enumerate(tiles):
            tile = Image.new('RGBA', (200, 200), background + (255,))
            tile.alpha_composite(artwork.resize((180, 180), Image.Resampling.LANCZOS), (10, 10))
            sheet.paste(tile.convert('RGB'), (index_x * 200, 0))
        sheet.save(args.debug)
    print(f"Snowflake icon: silhouette {mask.getbbox()}, art {art.width}x{art.height} on {CANVAS}px canvas, "
          f"bounds {base['catalogBounds']}, {(CATALOG / f'icons_{TAG}.json').stat().st_size / 1024:.0f} KB")


if __name__ == '__main__':
    main()
