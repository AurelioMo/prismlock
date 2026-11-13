"""Create the Orb fingerprint icon from the supplied glossy sphere render.

The source is a 1024px render of a purple/blue glass sphere on a near-white
backdrop with a soft drop shadow. The sphere keeps its own colours (the highlights
are part of the gloss), so alpha comes from a circular mask measured on the sphere's
own bounding box instead of a white key that would punch holes in the highlights.
"""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/assets/catalog'
PREVIEWS = ROOT / 'app/src/main/assets/previews'
DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/gemini-2.5-flash-image_quero_esse_negocio_que_enviei_em_imagem_monocromatico_roxo_e_azul-0.jpg')
TAG = 'orb'
CANVAS = 288
CONTENT = 216
ALPHA_THRESHOLD = 16


def sphere(image):
    """Bounding box of the saturated/dark sphere, leaving the grey shadow out."""
    red, green, blue = image.split()
    maximum = ImageChops.lighter(ImageChops.lighter(red, green), blue)
    minimum = ImageChops.darker(ImageChops.darker(red, green), blue)
    colorful = ImageChops.subtract(maximum, minimum).point(lambda value: 255 if value > 40 else 0)
    dark = minimum.point(lambda value: 255 if value < 150 else 0)
    return ImageChops.lighter(colorful, dark).getbbox()


def circular(size):
    """Anti-aliased circle: draw at 4x and downsample so the edge stays smooth."""
    scaled = size * 4
    mask = Image.new('L', (scaled, scaled), 0)
    ImageDraw.Draw(mask).ellipse((2, 2, scaled - 3, scaled - 3), fill=255)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def png(image):
    buffer = io.BytesIO()
    image.save(buffer, format='PNG', optimize=True)
    return buffer.getvalue()


def document(art, name):
    bounds = art.getchannel('A').point(lambda alpha: 255 if alpha >= ALPHA_THRESHOLD else 0).getbbox()
    centered = [bounds[0] - CANVAS / 2, bounds[1] - CANVAS / 2, bounds[2] - CANVAS / 2, bounds[3] - CANVAS / 2]
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    source = Image.open(args.source).convert('RGB')
    box = sphere(source)
    side = max(box[2] - box[0], box[3] - box[1])
    center = ((box[0] + box[2]) // 2, (box[1] + box[3]) // 2)
    square = (center[0] - side // 2, center[1] - side // 2,
              center[0] - side // 2 + side, center[1] - side // 2 + side)
    sphere_art = source.crop(square).resize((CONTENT, CONTENT), Image.Resampling.LANCZOS)
    sphere_art.putalpha(circular(CONTENT))
    art = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
    art.paste(sphere_art, ((CANVAS - CONTENT) // 2, (CANVAS - CONTENT) // 2))
    document_art = document(art, 'Orb')
    document_art['source_sha256'] = hashlib.sha256(args.source.read_bytes()).hexdigest()
    (CATALOG / f'icons_{TAG}.json').write_text(json.dumps(document_art, separators=(',', ':')))
    (PREVIEWS / f'icons_{TAG}.png').write_bytes(png(preview(art, document_art['catalogBounds'])))
    index_path = CATALOG / 'index.json'
    index = json.loads(index_path.read_text())
    index['icons'] = [entry for entry in index['icons'] if entry['tag'] != TAG]
    index['icons'].append({'tag': TAG, 'file': f'icons_{TAG}.json', 'frames': 1,
                           'external_source': args.source.name,
                           'source_sha256': document_art['source_sha256']})
    index_path.write_text(json.dumps(index, indent=2) + '\n')
    print(f"Orb icon: sphere {side}px from {args.source.name}, {CONTENT}px art on {CANVAS}px canvas, "
          f"bounds {document_art['catalogBounds']}, "
          f"{(CATALOG / f'icons_{TAG}.json').stat().st_size / 1024:.0f} KB")


if __name__ == '__main__':
    main()
