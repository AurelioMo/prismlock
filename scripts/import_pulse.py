"""Create the Pulse effect from the supplied 800x600 GIF loop.

Same pipeline as import_orbit: the black plate is keyed out by the brightest
channel, the artwork keeps its original pace over 13 consecutive frames, and the
sequence ends on a generated transparent frame with a fade, because Samsung only
stops the effect view in onAnimationEnd.

The source frame is 4:3 and carries a dim reflection under the ring, so the shared
crop is a square centred on the bright artwork (alpha>=64) that excludes it.

The loop is three 99-frame cycles of a slow breathing ring; only frames 0-24 carry
the pulse itself (the rest of the cycle holds). All 25 pulse frames are kept so the
motion plays through every stage at 60fps source cadence, which lands the effect on
0.417 seconds instead of skipping frames or speeding it up 6x.

The runtime normalises every effect's catalogBounds to the same 132-unit footprint,
so the drawn ring shrinks only if it covers less of those bounds. The ring is drawn
at 150 of the 288 canvas over its own blurred, dimmed halo that reaches the canvas
edges: the ring ends up about 45% smaller than a full-bleed effect, sitting on the
sensor like Pyro, while the visible halo still defines the bounds.
"""
import argparse
import hashlib
import json
import tempfile
from pathlib import Path

from PIL import Image, ImageFilter

from import_orbit import (CANVAS, CATALOG, FOOTPRINT, PREVIEWS, STEP, animation, decode,
                          keyed, png, preview)

DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/d7ac8fe75a93307683db992d9c8c6f8c.gif')
FRAMES = 25
PULSE_FOOTPRINT = 180
RING = 150
HALO = 284
HALO_ALPHA = 0.42
HALO_BLUR = 16
GLOW_FLOOR = 12
TAG = 'pulse'


def normalize(frames):
    """One shared square crop, centred on the bright artwork, for every frame."""
    union = None
    for frame in frames:
        visible = frame.getchannel('A').point(lambda alpha: 255 if alpha >= 64 else 0).getbbox()
        if visible:
            union = list(visible) if union is None else [min(union[0], visible[0]), min(union[1], visible[1]),
                                                         max(union[2], visible[2]), max(union[3], visible[3])]
    side = round(max(union[2] - union[0], union[3] - union[1]) * 1.10)
    left, top = (union[0] + union[2]) // 2 - side // 2, (union[1] + union[3]) // 2 - side // 2
    normalized = []
    for frame in frames:
        cropped = frame.crop((left, top, left + side, top + side))
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
        picked = normalize([keyed(frame, GLOW_FLOOR) for frame in decoded[:FRAMES]])
    picked.append(Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0)))
    document = animation(picked, 'Pulse', step=2, source_fps=60)
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
    print(f"Pulse: {FRAMES} visible frames + transparent ending from {len(decoded)} source frames, "
          f"{document['op'] / document['fr']:.2f}s at {document['fr']}fps, "
          f"{(CATALOG / f'animations_{TAG}.json').stat().st_size / 1024:.0f} KB")


if __name__ == '__main__':
    main()
