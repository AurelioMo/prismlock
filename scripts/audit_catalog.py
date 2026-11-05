"""Audit every decoded catalog frame and rank visual candidates (Pillow + NumPy).

Candidates are evidence for visual review, not automatic deletion decisions.
Animation comparisons sample the whole timeline at 64 relative phases and retain
native FPS/duration in the report. Theme variants are audited separately, not
classified as duplicate visible rows.
"""
import argparse
import base64
import hashlib
import io
import itertools
import json
from pathlib import Path
import subprocess
import numpy as np
from PIL import Image, ImageDraw
from consolidate_catalog import GROUPS

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'


def load(filename, baseline=None):
    if baseline:
        data = subprocess.check_output(['git', 'show', f'{baseline}:app/src/main/assets/catalog/{filename}'], cwd=ROOT)
    else:
        data = (ASSETS / 'catalog' / filename).read_bytes()
    return json.loads(data)


def inspect(entry, kind, baseline=None):
    document = load(entry['file'], baseline)
    assets = {}
    hashes = []
    for asset in document['assets']:
        image = Image.open(io.BytesIO(base64.b64decode(asset['p'].split(',', 1)[1]))).convert('RGBA')
        image.paste((0, 0, 0, 0), mask=image.getchannel('A').point(lambda a: 255 if a == 0 else 0))
        hashes.append(hashlib.sha256(str(image.size).encode() + image.tobytes()).hexdigest())
        assets[asset['id']] = image
    bounds = document['catalogBounds']
    target = 200 if kind == 'icons' else 132
    scale = target / max(bounds[2] - bounds[0], bounds[3] - bounds[1]) * 64 / 288
    cx = (bounds[0] + bounds[2]) / 2
    cy = (bounds[1] + bounds[3]) / 2
    normalized = []
    representatives = []
    for layer in document['layers']:
        image = assets[layer['refId']]
        transform = (1 / scale, 0, image.width / 2 + cx - 48 / scale,
                     0, 1 / scale, image.height / 2 + cy - 48 / scale)
        rendered = image.transform((96, 96), Image.Transform.AFFINE, transform, resample=Image.Resampling.BICUBIC)
        opacity = layer['ks']['o']['k'] / 100
        if opacity != 1:
            rendered.putalpha(rendered.getchannel('A').point(lambda a: round(a * opacity)))
        representatives.append(rendered)
        pixels = np.asarray(rendered.resize((32, 32), Image.Resampling.LANCZOS), dtype=np.float32) / 255
        pixels[:, :, :3] *= pixels[:, :, 3:4]
        normalized.append(pixels)
    count = 1 if kind == 'icons' else 64
    sampled = np.stack([normalized[round((len(normalized) - 1) * i / max(1, count - 1))] for i in range(count)])
    variants = []
    for variant in entry.get('variants', []):
        alt = inspect(dict(tag=entry['tag'] + '_' + variant,
                           file=entry['file'].replace('.json', '_' + variant + '.json')), kind, baseline)[0]
        variants.append(alt)
    record = dict(tag=entry['tag'], file=entry['file'], frames=len(hashes), fps=document.get('catalogSourceFps', document['fr']), timeline_fps=document['fr'],
                  duration_seconds=(document['op'] - document['ip']) / document['fr'],
                  bounds=bounds, frame_rgba_sha256=hashes, theme_variants=variants)
    return record, sampled, representatives[len(representatives) // 2]


def audit(index, baseline=None):
    records, nearest, thumbnails = {}, {}, {}
    for kind in ('icons', 'animations'):
        entries = [entry for root in index[kind] for entry in [root, *root.get('styles', [])]]
        values = []
        records[kind] = []
        for entry in entries:
            record, features, thumbnail = inspect(entry, kind, baseline)
            records[kind].append(record)
            values.append((entry['tag'], features))
            thumbnails[kind + ':' + entry['tag']] = thumbnail
        nearest[kind] = []
        for (a, first), (b, second) in itertools.combinations(values, 2):
            union = np.maximum(first[:, :, :, 3], second[:, :, :, 3]) > 0.025
            difference = np.abs(first - second)
            error = float(difference[union].mean()) if union.any() else 0
            alpha = float(difference[:, :, :, 3].mean())
            nearest[kind].append(dict(first=a, second=b, foreground_error=round(error, 6), alpha_error=round(alpha, 6)))
        nearest[kind].sort(key=lambda record: record['foreground_error'])
    return dict(entries=records, comparisons=nearest), thumbnails


def sheet(index, thumbnails, path):
    entries = [(kind, entry) for kind in ('icons', 'animations') for entry in index[kind]]
    canvas = Image.new('RGB', (1080, ((len(entries) + 5) // 6) * 140), '#111923')
    draw = ImageDraw.Draw(canvas)
    for i, (kind, entry) in enumerate(entries):
        x, y = i % 6 * 180, i // 6 * 140
        thumbnail = thumbnails[kind + ':' + entry['tag']]
        canvas.paste(thumbnail, (x + 42, y), thumbnail)
        draw.text((x + 5, y + 100), kind + '/' + entry['tag'], fill='white')
        if entry.get('styles'):
            draw.text((x + 5, y + 115), f"{1 + len(entry['styles'])} styles", fill='#A8C8FF')
    canvas.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--baseline-ref', default='v1.0.4')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    baseline_index = load('index.json', args.baseline_ref)
    current_index = load('index.json')
    baseline, _ = audit(baseline_index, args.baseline_ref)
    current, thumbnails = audit(current_index)
    decisions = {kind: [dict(family=family, style_label=label, retained_styles=[tag for tag, _ in styles])
                        for family, label, styles in groups] for kind, groups in GROUPS.items()}
    report = dict(method='Decoded RGBA, native bounds/opacity, all source frames, full-timeline phase comparison; visual review required',
                  baseline_ref=args.baseline_ref, baseline=baseline, current=current,
                  visible_families={kind: len(current_index[kind]) for kind in ('icons', 'animations')},
                  grouping_decisions=decisions, aliases=current_index.get('aliases', {}),
                  limits=['Color, geometry and different motion are retained as explicit styles.',
                          'Metrics rank candidates; they do not prove semantic equality by themselves.',
                          'Theme/AOD variants are intentionally distinct runtime resources.'])
    path = args.output / 'catalog-audit.json'
    path.write_text(json.dumps(report, indent=2) + '\n')
    sheet(current_index, thumbnails, args.output / 'catalog-after.png')
    print('AUDITED_ARTWORK_STYLES=' + str(sum(len(current['entries'][kind]) for kind in ('icons', 'animations'))))
    print('AUDITED_SOURCE_FRAMES=' + str(sum(record['frames'] for kind in ('icons', 'animations') for record in current['entries'][kind])))
    print('RESULT=success')


if __name__ == '__main__':
    main()
