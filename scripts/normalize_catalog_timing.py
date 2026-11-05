"""Retarget built-in image compositions to a 120Hz timebase without speeding them up.

Source PNGs and frame counts are unchanged. Motion.smooth interpolates opacity
between adjacent source frames; static icons never receive a redraw loop.
"""
import json
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / 'app/src/main/assets/catalog'
TARGET_FPS = 120


def normalize(document):
    if document['fr'] == TARGET_FPS and 'catalogFrameStep' in document:
        return False
    source = document['fr']
    factor = TARGET_FPS / source
    for layer in document['layers']:
        if layer['ty'] != 2:
            raise ValueError('This normalizer only supports the built-in image catalog')
        for property in layer['ks'].values():
            if isinstance(property, dict) and property.get('a', 0) != 0:
                raise ValueError('Unexpected animated transform in source artwork')
        for key in ('ip', 'op', 'st'):
            if key in layer:
                layer[key] *= factor
    for key in ('ip', 'op'):
        document[key] *= factor
    document['catalogSourceFps'] = source
    document['catalogFrameStep'] = factor
    document['fr'] = TARGET_FPS
    return True


def main():
    changed = 0
    for path in sorted(CATALOG.glob('*.json')):
        if path.name == 'index.json':
            continue
        document = json.loads(path.read_text())
        before = (document['op'] - document['ip']) / document['fr']
        if normalize(document):
            assert abs((document['op'] - document['ip']) / document['fr'] - before) < 1e-9
            path.write_text(json.dumps(document, separators=(',', ':')))
            changed += 1
    print(f'RETIMED_COMPOSITIONS={changed}')
    print('RESULT=success')


if __name__ == '__main__':
    main()
