"""Build explicit high-contrast light/AOD variants without changing icon geometry.

Requires Pillow. Source artwork remains embedded in the catalog, with provenance
recorded in index.json and source_sha256. Re-running this script is idempotent.
"""
import base64
import copy
import hashlib
import io
import json
from pathlib import Path

from PIL import Image, ImageChops

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'


def png(image):
    output = io.BytesIO()
    image.save(output, format='PNG')
    return output.getvalue()


def main():
    catalog = ASSETS / 'catalog'
    index = json.loads((catalog / 'index.json').read_text())
    for entry in index['icons']:
        tag = entry['tag']
        if tag not in ('aosp', 'oxygenos'):
            continue
        document = json.loads((catalog / entry['file']).read_text())
        encoded = document['assets'][0]['p'].split(',', 1)[1]
        original = base64.b64decode(encoded)
        document['source_sha256'] = document.get('source_sha256', hashlib.sha256(original).hexdigest())
        source = Image.open(io.BytesIO(original)).convert('RGBA')
        alpha = source.getchannel('A')
        prepared = document.get('catalogContrastPrepared', False)
        if tag == 'aosp' and not prepared:
            # The source is white fingerprint strokes on an opaque black disk.
            # Recoloring its alpha alone would turn the whole disk into a solid dot.
            alpha = ImageChops.multiply(alpha, source.convert('L'))
            document['catalogContrastPrepared'] = True
        # AOSP's source is almost black, so it also needs a visible dark-theme base.
        variants = [('light', 0), ('aod', 255)]
        if tag == 'aosp':
            variants.append(('', 255))
        for suffix, value in variants:
            image = Image.new('RGBA', source.size, (value, value, value, 255))
            image.putalpha(alpha)
            alternate = copy.deepcopy(document)
            alternate['assets'][0]['p'] = 'data:image/png;base64,' + base64.b64encode(png(image)).decode()
            filename = f'icons_{tag}' + (f'_{suffix}' if suffix else '') + '.json'
            (catalog / filename).write_text(json.dumps(alternate, separators=(',', ':')))
            assert image.getchannel('A').tobytes() == alpha.tobytes()
        if tag == 'oxygenos':
            (catalog / entry['file']).write_text(json.dumps(document, separators=(',', ':')))
        else:
            preview = ASSETS / 'previews/icons_aosp.png'
            old = Image.open(preview).convert('RGBA')
            light = Image.new('RGBA', old.size, 'white')
            preview_alpha = old.getchannel('A')
            if not prepared:
                preview_alpha = ImageChops.multiply(preview_alpha, old.convert('L'))
            light.putalpha(preview_alpha)
            preview.write_bytes(png(light))
        entry['variants'] = ['light', 'aod']
        print(f'PASS: {tag} explicit theme contrast; stroke geometry preserved')
    (catalog / 'index.json').write_text(json.dumps(index, indent=2))


if __name__ == '__main__':
    main()
