"""Import only visual resources from the user-provided Keqing overlay (Pillow required).

The input APK is read-only. Its executable code, audio and video container are not
bundled. The complete 60fps PNG effect is preferred over its 30fps/video duplicates.
"""
import argparse
import base64
import copy
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
SOURCE_SHA256 = '6cd1a77d6f5826ee0c3f9886cd31edefc37b01947ee41b96555add85e6969087'


def png(image):
    output = io.BytesIO()
    image.save(output, format='PNG', optimize=True)
    return output.getvalue()


def document(images, tag, static=False):
    assets, layers, bounds = [], [], None
    duration = 90 if static else len(images)
    for i, original in enumerate(images):
        image = original.convert('RGBA')
        image.thumbnail((288, 288), Image.Resampling.LANCZOS)
        # Remove invisible RGB garbage only; visible pixels and alpha are retained.
        image.paste((0, 0, 0, 0), mask=image.getchannel('A').point(lambda a: 255 if a == 0 else 0))
        width, height = image.size
        visible = image.getchannel('A').point(lambda a: 255 if a >= 16 else 0).getbbox()
        if visible:
            centered = [visible[0] - width / 2, visible[1] - height / 2,
                        visible[2] - width / 2, visible[3] - height / 2]
            bounds = centered if bounds is None else [min(bounds[0], centered[0]), min(bounds[1], centered[1]),
                                                      max(bounds[2], centered[2]), max(bounds[3], centered[3])]
        identity = f'frame_{i}'
        assets.append(dict(id=identity, w=width, h=height, u='', e=1,
                           p='data:image/png;base64,' + base64.b64encode(png(image)).decode()))
        layers.append(dict(ddd=0, ind=i + 1, ty=2, nm=identity, refId=identity, sr=1,
                           ip=0 if static else i, op=duration if static else i + 1, st=0, bm=0,
                           ks=dict(o=dict(a=0, k=100), r=dict(a=0, k=0), p=dict(a=0, k=[144, 144, 0]),
                                   a=dict(a=0, k=[width / 2, height / 2, 0]), s=dict(a=0, k=[100, 100, 100]))))
    if bounds is None:
        raise ValueError('The resource sequence has no visible artwork')
    return dict(v='5.9.1', fr=60, ip=0, op=duration, w=288, h=288, nm=tag,
                ddd=0, assets=assets, layers=layers, markers=[], catalogBounds=bounds,
                catalogAlphaThreshold=16, source_sha256=SOURCE_SHA256)


def preview(composition, path):
    # Pick a visible representative; fade-out's last frame is intentionally blank.
    assets = composition['assets']
    asset = assets[len(assets) // 2] if len(assets) > 1 else assets[0]
    image = Image.open(io.BytesIO(base64.b64decode(asset['p'].split(',', 1)[1]))).convert('RGBA')
    bounds = composition['catalogBounds']
    scale = 112 / max(bounds[2] - bounds[0], bounds[3] - bounds[1])
    cx, cy = image.width / 2 + (bounds[0] + bounds[2]) / 2, image.height / 2 + (bounds[1] + bounds[3]) / 2
    image = image.transform((144, 144), Image.Transform.AFFINE,
                            (1 / scale, 0, cx - 72 / scale, 0, 1 / scale, cy - 72 / scale),
                            resample=Image.Resampling.BICUBIC)
    path.write_bytes(png(image))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    args = parser.parse_args()
    if hashlib.sha256(args.apk.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError('This importer is pinned to the inspected Keqing APK')
    index_path = ASSETS / 'catalog/index.json'
    index = json.loads(index_path.read_text())
    with zipfile.ZipFile(args.apk) as archive:
        families = [('icons', 'keqing', ['res/drawable-nodpi-v4/kgd_osfingerprint_icon.png']),
                    ('animations', 'keqing', [f'res/drawable-nodpi-v4/kgd_fingerprint_element_60fps_{i}.png' for i in range(60)]),
                    ('animations', 'keqing_appear', [f'res/drawable-nodpi-v4/kgd_osfingerprint_fadein_{i}.png' for i in range(25)]),
                    ('animations', 'keqing_disappear', [f'res/drawable-nodpi-v4/kgd_osfingerprint_fadeout_{i}.png' for i in range(25)])]
        for kind, tag, source in families:
            images = [Image.open(io.BytesIO(archive.read(name))).convert('RGBA') for name in source]
            if kind == 'animations' and tag == 'keqing':
                images.append(Image.new('RGBA', images[0].size))
            composition = document(images, tag, static=kind == 'icons')
            filename = f'{kind}_{tag}.json'
            (ASSETS / 'catalog' / filename).write_text(json.dumps(composition, separators=(',', ':')))
            preview(composition, ASSETS / 'previews' / f'{kind}_{tag}.png')
            entry = dict(tag=tag, file=filename, frames=len(images), source=source,
                         external_source='KeqingFingerPrint.apk', source_sha256=SOURCE_SHA256)
            if kind == 'icons':
                # Preserve the original 40% opacity, including in light and AOD variants.
                entry['variants'] = ['light', 'aod']
                for variant, color in [('light', 0), ('aod', 255)]:
                    alternate = copy.deepcopy(composition)
                    asset = alternate['assets'][0]
                    original = Image.open(io.BytesIO(base64.b64decode(asset['p'].split(',', 1)[1]))).convert('RGBA')
                    image = Image.new('RGBA', original.size, (color, color, color, 0))
                    image.putalpha(original.getchannel('A'))
                    asset['p'] = 'data:image/png;base64,' + base64.b64encode(png(image)).decode()
                    (ASSETS / 'catalog' / f'icons_keqing_{variant}.json').write_text(json.dumps(alternate, separators=(',', ':')))
            if tag == 'keqing' and kind == 'animations':
                entry['terminal_frame'] = 'generated-transparent'
            known = [existing['tag'] for root in index[kind] for existing in [root, *root.get('styles', [])]]
            if tag not in known:
                index[kind].append(entry)
            print(f'IMPORTED={kind}:{tag} FRAMES={len(images)} FPS=60')
    index_path.write_text(json.dumps(index, indent=2) + '\n')
    # Keep the source cadence while publishing all built-in timelines on the same 120Hz base.
    from normalize_catalog_timing import main as normalize_timing
    normalize_timing()
    print('RESULT=success')


if __name__ == '__main__':
    main()
