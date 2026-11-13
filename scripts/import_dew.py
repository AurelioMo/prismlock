"""Create the Dew effect from the supplied 800x600 glass-drop video.

Same pipeline as import_orbit: the black plate is keyed out by the brightest channel,
one shared crop and scale keep the drop from jittering, and the sequence ends on a
generated transparent frame.

The clip is a nine second loop of a glass drop breathing in place, and its brightness
oscillates with a period of about 62 frames. One whole pulse is taken — 16 frames four
source frames apart — and each frame is held ten playback frames, so the ripple
plays exactly once over 1.42 seconds at 120fps.

The drop has to wrap the fingerprint instead of sitting on top of it, so the ring is
drawn at 210 of the 288 canvas inside its own blurred, dimmed halo, as Pulse and Tide
do: the halo defines catalogBounds, which the runtime maps to the effect footprint,
and the visible ring surrounds the icon. Each frame is held ten playback frames,
which stretches one pulse of the source to 1.42 seconds, the slowest the catalog
gate allows.
"""
import argparse
import hashlib
import json
import tempfile
from pathlib import Path

from PIL import Image, ImageFilter

from import_orbit import (CANVAS, CATALOG, PREVIEWS, animation, decode, keyed, png, preview)

DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/e0ffd703768562ef2ea40a59ab4c80b3_t3.mp4')
FRAMES = 16
START = 55
STRIDE = 4
# Each frame is held for STEP playback frames, so the pulse keeps the pace of the
# source instead of flashing past in half a second. The catalog gate caps an effect
# at 1.5 seconds, so this is the slowest the whole pulse can play: 1.42 seconds.
STEP = 10
# The ring has to wrap the icon, not sit outside it: 210 of the 288 canvas leaves the
# fingerprint inside the drop while the halo below still defines the bounds.
RING = 210
HALO = 284
HALO_ALPHA = 0.42
HALO_BLUR = 16
PLATE_FLOOR = 24
# Dark glass only lights up on its rim, so the keyed alpha is lifted before scaling:
# without it the effect is too faint to read on the sensor.
ALPHA_GAMMA = 0.62
ALPHA_FLOOR = 6
TAG = 'dew'


def boosted(frame):
    """Key the plate out, then lift the thin rim so the glass reads on the sensor."""
    image = keyed(frame, PLATE_FLOOR)
    alpha = image.getchannel('A').point(
        lambda value: 0 if value < ALPHA_FLOOR else min(255, round((value / 255) ** ALPHA_GAMMA * 255)))
    image.putalpha(alpha)
    return image


def normalize(frames):
    """Ring plus its own halo, so the effect wraps the sensor as an aura."""
    union = None
    for frame in frames:
        visible = frame.getchannel('A').point(lambda alpha: 255 if alpha >= 16 else 0).getbbox()
        if visible:
            union = list(visible) if union is None else [min(union[0], visible[0]), min(union[1], visible[1]),
                                                         max(union[2], visible[2]), max(union[3], visible[3])]
    width, height = union[2] - union[0], union[3] - union[1]
    side = max(width, height)
    left = union[0] + width // 2 - side // 2
    top = union[1] + height // 2 - side // 2
    square_crop = (left, top, left + side, top + side)
    normalized = []
    for frame in frames:
        cropped = frame.crop(square_crop)
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
        window = decoded[START:START + FRAMES * STRIDE:STRIDE]
        picked = normalize([boosted(frame) for frame in window])
    picked.append(Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0)))
    document = animation(picked, 'Dew', step=STEP, source_fps=120 // STEP)
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
    print(f"Dew: {FRAMES} visible frames + transparent ending from {len(decoded)} source frames, "
          f"{document['op'] / document['fr']:.2f}s at {document['fr']}fps, "
          f"bounds {document['catalogBounds']}, "
          f"{(CATALOG / f'animations_{TAG}.json').stat().st_size / 1024:.0f} KB")


if __name__ == '__main__':
    main()
