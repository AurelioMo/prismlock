"""Apply reviewed visual-family decisions; names alone never imply duplication.

Each retained style keeps its exact artwork, timing and saved identifier. The one
legacy outline alias resolves to the sharper source of the same wobble-ring design.
See docs/catalog-audit.md for observations and image/timeline comparison methodology.
"""
import copy
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
GROUPS = {
    'icons': [
        ('white', 'White', [('bluegradient', 'Blue gradient')]),
        ('originos', 'White', [('originosblue', 'Blue')]),
        ('star', 'White', [('starblue', 'Blue')]),
        ('nothingos', 'White ring', [('coloros17_simple', 'Gray ring')]),
        ('oxygenos', 'Colored glow', [('coloros17_ring', 'White glow')]),
        ('coloros17_swirl', 'ColorOS 17', [('coloros2', 'Original strokes')]),
    ],
    'animations': [
        ('coloros17_ring', 'ColorOS 17', [('oxygenos', 'Original')]),
        ('coloros17_fizz', 'ColorOS 17', [('dust', 'Original')]),
        ('originos', 'Pink', [('originosblue', 'Blue')]),
        ('keqing', 'Electro', [('keqing_appear', 'Appear'), ('keqing_disappear', 'Disappear')]),
    ],
}


def main():
    path = ASSETS / 'catalog/index.json'
    catalog = json.loads(path.read_text())
    for kind, groups in GROUPS.items():
        entries = {}
        for root in catalog[kind]:
            for original in [root, *root.get('styles', [])]:
                entry = copy.deepcopy(original)
                entry.pop('styles', None)
                entries[entry['tag']] = entry
        children = set()
        for family, label, styles in groups:
            entry = entries[family]
            entry['style_label'] = label
            entry['styles'] = []
            for tag, style_label in styles:
                alternate = entries[tag]
                alternate['label'] = style_label
                entry['styles'].append(alternate)
                children.add(tag)
        catalog[kind] = [entry for tag, entry in entries.items() if tag not in children and tag != ('outline' if kind == 'icons' else '')]
    catalog['aliases'] = {'icons': {'outline': 'coloros17_bubble'}, 'animations': {}}
    for directory, extension in [('catalog', 'json'), ('previews', 'png')]:
        (ASSETS / directory / f'icons_outline.{extension}').unlink(missing_ok=True)
    path.write_text(json.dumps(catalog, indent=2) + '\n')
    print('FAMILIES=' + str({kind: len(catalog[kind]) for kind in GROUPS}))
    print('RESULT=success')


if __name__ == '__main__':
    main()
