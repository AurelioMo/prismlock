"""Create the Tide effect from the supplied 600x600 video loop.

Same pipeline as import_orbit: the black plate is keyed out by the brightest
channel so the plate noise and the square haze disappear while the cyan glow keeps
its soft falloff, one shared crop and scale keep the ring from jittering, and the
sequence ends on a generated transparent frame.

The source is 18 seconds of a continuously evolving energy ring, far longer than an
effect may stay on screen, so a single coherent stretch of the loop is used. 16
consecutive frames of the 30fps source are shown four playback frames each, which
lands the effect on 0.53 seconds at 120fps without speeding the waves up.
"""
import argparse
import hashlib
import json
import tempfile
from pathlib import Path

from PIL import Image

from import_orbit import (CANVAS, CATALOG, FOOTPRINT, PREVIEWS, animation, decode, keyed,
                          png, preview)

DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/915d00b7556e4079f5d14930d3ac720a_t3.mp4')
FRAMES = 16
START = 60
PLATE_FLOOR = 24
TAG = 'tide'


def normalize(frames):
    """One shared crop and scale for every frame, as the other effects use."""
    union = None
    for frame in frames:
        visible = frame.getchannel('A').point(lambda alpha: 255 if alpha >= 16 else 0).getbbox()
        if visible:
            union = list(visible) if union is None else [min(union[0], visible[0]), min(union[1], visible[1]),
                                                         max(union[2], visible[2]), max(union[3], visible[3])]
    width, height = union[2] - union[0], union[3] - union[1]
    scale = FOOTPRINT / max(width, height)
    size = (round(width * scale), round(height * scale))
    normalized = []
    for frame in frames:
        image = frame.crop(union).resize(size, Image.Resampling.LANCZOS)
        canvas = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
        canvas.paste(image, ((CANVAS - size[0]) // 2, (CANVAS - size[1]) // 2))
        normalized.append(canvas)
    return normalized


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    digest = hashlib.sha256(args.source.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory() as directory:
        decoded = decode(args.source, directory)
        picked = normalize([keyed(frame, PLATE_FLOOR) for frame in decoded[START:START + FRAMES]])
    picked.append(Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0)))
    document = animation(picked, 'Tide')
    document['source_sha256'] = digest
    (CATALOG / f'animations_{TAG}.json').write_text(json.dumps(document, separators=(',', ':')))
    (PREVIEWS / f'animations_{TAG}.png').write_bytes(png(preview(document)))
    index_path = CATALOG / 'index.json'
    index = json.loads(index_path.read_text())
    index['animations'] = [entry for entry in index['animations'] if entry['tag'] != TAG]
    index['animations'].append({'tag': TAG, 'file': f'animations_{TAG}.json',
                                'frames': len(document['assets']), 'terminal_frame': 'generated-transparent',
                                'external_source': args.source.name, 'source_sha256': digest})
    index_path.write_text(json.dumps(index, indent=2) + '\n')
    print(f"Tide: {FRAMES} visible frames + transparent ending from {len(decoded)} source frames, "
          f"{document['op'] / document['fr']:.2f}s at {document['fr']}fps, "
          f"{(CATALOG / f'animations_{TAG}.json').stat().st_size / 1024:.0f} KB")


if __name__ == '__main__':
    main()
