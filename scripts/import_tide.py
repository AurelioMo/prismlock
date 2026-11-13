"""Create the Tide effect from the supplied 600x600 video loop.

Same pipeline as import_orbit: the black plate is keyed out by the brightest
channel so the plate noise and the square haze disappear while the cyan glow keeps
its soft falloff, one shared crop and scale keep the ring from jittering, and the
sequence ends on a generated transparent frame.

The source is 18 seconds of a slowly evolving energy ring. Taking consecutive frames
barely changes between them, which read as a still image on the sensor, so frames are
sampled six source frames apart: 16 frames cover 3.2 seconds of the loop and play over
four playback frames each, which lands the effect on 0.53 seconds at 120fps, about six
times the source pace, and the waves visibly move.

The ring has to sit on the sensor like Pulse instead of filling the artboard, so the
shared crop is scaled to 170 of the 288 canvas rather than the 240 the full-bleed
effects use.
"""
import argparse
import hashlib
import json
import tempfile
from pathlib import Path

from PIL import Image, ImageFilter

from import_orbit import (CANVAS, CATALOG, PREVIEWS, animation, decode, keyed, png, preview)

DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/915d00b7556e4079f5d14930d3ac720a_t3.mp4')
FRAMES = 16
START = 60
STRIDE = 6
RING = 150
HALO = 284
HALO_ALPHA = 0.42
HALO_BLUR = 16
PLATE_FLOOR = 24
TAG = 'tide'


def normalize(frames):
    """Ring plus its own halo, so the effect reads as an aura around the sensor.

    The runtime maps catalogBounds to a fixed footprint, so a tight crop makes the
    visible ring fill that footprint and float away from the fingerprint. Like Pulse,
    the ring is drawn small inside a blurred, dimmed copy that reaches the canvas
    edges: the halo defines the bounds while the ring stays near the sensor.
    """
    union = None
    for frame in frames:
        visible = frame.getchannel('A').point(lambda alpha: 255 if alpha >= 16 else 0).getbbox()
        if visible:
            union = list(visible) if union is None else [min(union[0], visible[0]), min(union[1], visible[1]),
                                                         max(union[2], visible[2]), max(union[3], visible[3])]
    normalized = []
    for frame in frames:
        cropped = frame.crop(union)
        ring = cropped.resize((RING, RING), Image.Resampling.LANCZOS)
        halo = cropped.resize((HALO, HALO), Image.Resampling.LANCZOS).filter(
            ImageFilter.GaussianBlur(HALO_BLUR))
        halo.putalpha(halo.getchannel('A').point(lambda alpha: round(alpha * HALO_ALPHA)))
        canvas = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
        canvas.alpha_composite(halo, ((CANVAS - HALO) // 2, (CANVAS - HALO) // 2))
        canvas.alpha_composite(ring, ((CANVAS - RING) // 2, (CANVAS - RING) // 2))
        normalized.append(canvas)
    return normalized


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    digest = hashlib.sha256(args.source.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory() as directory:
        decoded = decode(args.source, directory)
        sample = decoded[START:START + FRAMES * STRIDE:STRIDE]
        picked = normalize([keyed(frame, PLATE_FLOOR) for frame in sample])
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
