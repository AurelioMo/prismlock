import base64
import binascii
import json
import re
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/assets/catalog'
PREVIEWS = ROOT / 'app/src/main/assets/previews'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_png(data, dimensions=None):
    require(data[:8] == b'\x89PNG\r\n\x1a\n', 'Invalid PNG signature')
    offset = 8
    ended = False
    while offset < len(data):
        require(offset + 12 <= len(data), 'Truncated PNG chunk')
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        chunk = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        require(offset + length + 12 <= len(data), 'Truncated PNG payload')
        checksum = struct.unpack('>I', data[offset + 8 + length:offset + 12 + length])[0]
        require(binascii.crc32(chunk + payload) & 0xffffffff == checksum, 'PNG checksum mismatch')
        if chunk == b'IHDR':
            width, height, depth, color = struct.unpack('>IIBB', payload[:10])
            require(width > 0 and height > 0 and depth == 8 and color == 6, 'Expected RGBA PNG')
            if dimensions is not None:
                require((width, height) == dimensions, 'PNG dimensions do not match asset')
        offset += length + 12
        if chunk == b'IEND':
            ended = True
            break
    require(ended and offset == len(data), 'Invalid PNG ending')


def check_document(filename, static):
    document = json.loads((CATALOG / filename).read_text())
    require(document['w'] == document['h'] == 288, filename + ': invalid canvas')
    require(document['fr'] == 120 and document['op'] > 0, filename + ': expected 120Hz timing')
    step = document['catalogFrameStep']
    require(step > 0 and abs(document['catalogSourceFps'] * step - 120) < 1e-6, filename + ': invalid source cadence')
    require(static or document['op'] / document['fr'] <= 1.5, filename + ': effect exceeds 1.5 seconds')
    require('catalogFootprint' not in document, filename + ': use the shared footprint')
    bounds = document['catalogBounds']
    require(len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1], filename + ': empty bounds')
    require(document['catalogAlphaThreshold'] in (16, 128), filename + ': invalid alpha threshold')
    assets = {asset['id']: asset for asset in document['assets']}
    require(len(assets) == len(document['assets']) == len(document['layers']), filename + ': asset/layer mismatch')
    for position, layer in enumerate(document['layers']):
        require(layer['ty'] == 2 and layer['refId'] in assets, filename + ': unsupported layer')
        require(abs(layer['ip'] - (0 if static else position * step)) < 1e-6, filename + ': invalid layer start')
        require(abs(layer['op'] - (document['op'] if static else (position + 1) * step)) < 1e-6, filename + ': invalid layer end')
        asset = assets[layer['refId']]
        require(asset['e'] == 1 and asset['u'] == '', filename + ': non-embedded asset')
        prefix, encoded = asset['p'].split(',', 1)
        require(prefix == 'data:image/png;base64', filename + ': unsupported image format')
        check_png(base64.b64decode(encoded, validate=True), (asset['w'], asset['h']))
    return document


def main():
    index = json.loads((CATALOG / 'index.json').read_text())
    expected_files = {'index.json'}
    expected_previews = set()
    count = 0
    for kind in ('icons', 'animations'):
        tags = set()
        for entry in [entry for root in index[kind] for entry in [root, *root.get('styles', [])]]:
            tag = entry['tag']
            filename = entry['file']
            require(re.fullmatch(r'[a-z0-9_]+', tag) is not None and tag not in tags, 'Invalid or duplicate tag')
            tags.add(tag)
            require(filename == kind + '_' + tag + '.json', 'Invalid catalog filename')
            expected_files.add(filename)
            document = check_document(filename, kind == 'icons')
            require(len(document['assets']) == entry['frames'], filename + ': incorrect frame count')
            if entry.get('external_source'):
                require(re.fullmatch(r'[a-f0-9]{64}', entry['source_sha256']) is not None, 'Invalid source hash')
                require(document['source_sha256'] == entry['source_sha256'], 'Source hash mismatch')
            for variant in entry.get('variants', []):
                require(kind == 'icons' and variant in ('light', 'aod'), 'Unknown variant')
                alternate_name = filename.replace('.json', '_' + variant + '.json')
                expected_files.add(alternate_name)
                alternate = check_document(alternate_name, True)
                require(alternate['catalogBounds'] == document['catalogBounds'], 'Variant sizing mismatch')
                require(alternate['source_sha256'] == document['source_sha256'], 'Variant source mismatch')
            if tag == 'aqua' and kind == 'animations':
                require(document['fr'] == 120 and document['op'] == 96 and document['catalogSourceFps'] == 30, 'Aqua must last 0.8 seconds')
                require(document['layers'][-1]['ks']['o']['k'] == 0, 'Aqua final frame must be invisible')
                opacity = [layer['ks']['o']['k'] for layer in document['layers'][-5:]]
                require(opacity == [100, 75, 50, 25, 0], 'Aqua fade-out regression')
            preview = kind + '_' + tag + '.png'
            expected_previews.add(preview)
            check_png((PREVIEWS / preview).read_bytes())
            count += 1
    for kind in ('icons', 'animations'):
        all_tags = {entry['tag'] for root in index[kind] for entry in [root, *root.get('styles', [])]}
        root_tags = {entry['tag'] for entry in index[kind]}
        aliases = index.get('aliases', {}).get(kind, {})
        for legacy, target in aliases.items():
            require(re.fullmatch(r'[a-z0-9_]+', legacy) is not None and legacy not in all_tags, 'Invalid alias source')
            require(target in root_tags and target not in aliases, 'Alias must resolve directly to a visible family')
        for root in index[kind]:
            for style in root.get('styles', []):
                require(bool(style.get('label')) and not style.get('styles'), 'Invalid style definition')
    require({path.name for path in CATALOG.iterdir()} == expected_files, 'Missing or unreferenced catalog files')
    require({path.name for path in PREVIEWS.iterdir()} == expected_previews, 'Missing or unreferenced previews')
    manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
    require('io.github.aureliomo.prismlock.config' in manifest, 'Provider identity mismatch')
    metadata = ROOT / 'app/src/main/resources/META-INF/xposed'
    require((metadata / 'java_init.list').read_text().strip() == 'io.github.aureliomo.prismlock.Hook', 'Xposed entrypoint mismatch')
    require((metadata / 'scope.list').read_text().strip() == 'com.samsung.android.biometrics.app.setting', 'Xposed scope mismatch')
    properties = dict(line.split('=', 1) for line in (metadata / 'module.prop').read_text().splitlines() if line)
    require(properties['minApiVersion'] == properties['targetApiVersion'] == '102', 'Expected modern API 102')
    require(not (ROOT / 'app/src/main/assets/xposed_init').exists(), 'Legacy entrypoint must not be packaged')
    print(f'PASS: {sum(len(index[kind]) for kind in ("icons", "animations"))} visible families / {count} artwork styles; embedded PNG integrity, aliases, variants, timing and package identity')


if __name__ == '__main__':
    main()
