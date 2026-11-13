"""Create the Orbit effect from the supplied 540px video loop.

The video carries an opaque black plate, so every sampled frame is keyed by its
brightest channel: near-black plate noise and the video's square haze fall below
the floor and become fully transparent, while the glow keeps its soft falloff and
white core.

Frames stay consecutive so the rotation keeps its original pace (13 frames of the
25fps source shown over 0.467 seconds is about 1.1x real time). Sampling the whole
7.2 second loop into a short effect would spin the rings 4-5x too fast and make
Motion's crossfade look doubled.

Samsung's VisualEffectContainer only stops the effect view in onAnimationEnd, so
the composition duration is exactly how long the artwork stays on screen after the
fingerprint is accepted. 0.467 seconds matches the catalog's shorter effects; the
fade to a transparent ending leaves nothing drawn once it stops. The shared crop
and scale keep the art at 240 of the 288 canvas so it reads the same size as the
other effects instead of covering the whole canvas.
"""
import argparse
import base64
import hashlib
import io
import json
import subprocess
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/assets/catalog'
PREVIEWS = ROOT / 'app/src/main/assets/previews'
DEFAULT_SOURCE = Path('/Users/aurelio/Downloads/ezgif-6db9cb6dad50adda.mp4')
FRAMES = 13
PLAYBACK_FPS = 120
SOURCE_FPS = 30
STEP = PLAYBACK_FPS // SOURCE_FPS
PLATE_FLOOR = 22
CANVAS = 288
FOOTPRINT = 240


def decode(source, directory):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(source), '-fps_mode', 'passthrough',
                    str(Path(directory) / 'f_%04d.png')], check=True)
    return sorted(Path(directory).glob('f_*.png'))


def keyed(frame, floor=PLATE_FLOOR):
    """Drop the black plate, recover the glow's own color and keep alpha soft."""
    image = Image.open(frame).convert('RGB')
    pixels = list(image.getdata())
    out = Image.new('RGBA', image.size, (0, 0, 0, 0))
    converted = []
    span = 255 - floor
    for red, green, blue in pixels:
        peak = max(red, green, blue)
        alpha = 0 if peak <= floor else min(255, round((peak - floor) * 255 / span))
        if alpha == 0:
            converted.append((0, 0, 0, 0))
            continue
        gain = 255 / peak
        converted.append((min(255, round(red * gain)), min(255, round(green * gain)),
                          min(255, round(blue * gain)), alpha))
    out.putdata(converted)
    return out


def png(image):
    buffer = io.BytesIO()
    image.save(buffer, format='PNG', optimize=True)
    return buffer.getvalue()


def normalize(frames):
    """One shared crop and scale for every frame keeps the art from jittering."""
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


def animation(frames, name, step=STEP, source_fps=SOURCE_FPS):
    assets, layers, bounds = [], [], None
    for index, frame in enumerate(frames):
        image = frame.copy()
        visible = image.getchannel('A').point(lambda alpha: 255 if alpha >= 16 else 0).getbbox()
        if visible:
            centered = [visible[0] - image.width / 2, visible[1] - image.height / 2,
                        visible[2] - image.width / 2, visible[3] - image.height / 2]
            bounds = centered if bounds is None else [min(bounds[0], centered[0]), min(bounds[1], centered[1]),
                                                      max(bounds[2], centered[2]), max(bounds[3], centered[3])]
        identity = 'frame_' + str(index)
        assets.append({'id': identity, 'w': image.width, 'h': image.height, 'u': '', 'e': 1,
                       'p': 'data:image/png;base64,' + base64.b64encode(png(image)).decode()})
        layers.append({'ddd': 0, 'ind': index + 1, 'ty': 2, 'nm': identity, 'refId': identity,
                       'sr': 1, 'ip': index * step, 'op': (index + 1) * step, 'st': 0, 'bm': 0,
                       'ks': {'o': {'a': 0, 'k': 100}, 'r': {'a': 0, 'k': 0},
                              'p': {'a': 0, 'k': [144, 144, 0]},
                              'a': {'a': 0, 'k': [image.width / 2, image.height / 2, 0]},
                              's': {'a': 0, 'k': [100, 100, 100]}}})
    # Like Aqua and Keqing, end on nothing: the last visible frame fades to a
    # generated transparent frame so the effect leaves the screen after unlocking.
    for position, layer in enumerate(layers):
        layer['ks']['o']['k'] = min(100, max(0, (len(layers) - 1 - position) * 25))
    return {'v': '5.9.1', 'fr': PLAYBACK_FPS, 'ip': 0, 'op': len(layers) * step, 'w': CANVAS, 'h': CANVAS,
            'nm': name, 'ddd': 0, 'assets': assets, 'layers': layers, 'markers': [],
            'catalogBounds': bounds, 'catalogAlphaThreshold': 16,
            'catalogSourceFps': source_fps, 'catalogFrameStep': step}


def preview(document):
    asset = document['assets'][len(document['assets']) // 2]
    image = Image.open(io.BytesIO(base64.b64decode(asset['p'].split(',', 1)[1]))).convert('RGBA')
    bounds = document['catalogBounds']
    scale = 112 / max(bounds[2] - bounds[0], bounds[3] - bounds[1])
    center_x, center_y = image.width / 2 + (bounds[0] + bounds[2]) / 2, image.height / 2 + (bounds[1] + bounds[3]) / 2
    return image.transform((144, 144), Image.Transform.AFFINE,
                           (1 / scale, 0, center_x - 72 / scale, 0, 1 / scale, center_y - 72 / scale),
                           resample=Image.Resampling.BICUBIC)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('video', nargs='?', type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()
    digest = hashlib.sha256(args.video.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory() as directory:
        decoded = decode(args.video, directory)
        picked = normalize([keyed(frame) for frame in decoded[:FRAMES]])
    picked.append(Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0)))
    document = animation(picked, 'Orbit')
    document['source_sha256'] = digest
    (CATALOG / 'animations_orbit.json').write_text(json.dumps(document, separators=(',', ':')))
    (PREVIEWS / 'animations_orbit.png').write_bytes(png(preview(document)))
    index_path = CATALOG / 'index.json'
    index = json.loads(index_path.read_text())
    index['animations'] = [entry for entry in index['animations'] if entry['tag'] != 'orbit']
    index['animations'].append({'tag': 'orbit', 'file': 'animations_orbit.json',
                                'frames': len(document['assets']), 'terminal_frame': 'generated-transparent',
                                'external_source': args.video.name, 'source_sha256': digest})
    index_path.write_text(json.dumps(index, indent=2) + '\n')
    print(f"Orbit: {FRAMES} visible frames + transparent ending from {len(decoded)} source frames, "
          f"{document['op'] / document['fr']:.2f}s at {PLAYBACK_FPS}fps, "
          f"{(CATALOG / 'animations_orbit.json').stat().st_size / 1024:.0f} KB")


if __name__ == '__main__':
    main()
