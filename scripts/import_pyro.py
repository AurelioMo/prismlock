"""Create the white Pyro fingerprint icon from the supplied transparent-style image."""
import argparse, base64, hashlib, io, json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'app/src/main/assets/catalog'
PREVIEWS = ROOT / 'app/src/main/assets/previews'
DEFAULT_SOURCE = Path('/var/folders/7m/vtsn8cg54fv760q8r_y7fkq80000gn/T/codex-clipboard-e7b56f05-9265-4060-8f3d-ba201a0418c6.png')
TAG = 'pyro'
SOURCE = DEFAULT_SOURCE

def png(image):
    out = io.BytesIO(); image.save(out, 'PNG', optimize=True); return out.getvalue()

def extract_shape():
    src = Image.open(SOURCE).convert('RGB')
    pix = src.load(); alpha = Image.new('L', src.size); ap = alpha.load()
    for y in range(src.height):
        for x in range(src.width):
            r,g,b = pix[x,y]
            # Chroma separates the orange emblem from the grayscale checkerboard.
            chroma = r - max(g,b)
            ap[x,y] = max(0, min(255, int((chroma - 8) * 255 / 175)))
    bbox = alpha.getbbox()
    if not bbox: raise ValueError('Pyro image has no colored foreground')
    src = src.crop(bbox); alpha = alpha.crop(bbox)
    # Match Keqing's 216px source footprint while preserving the emblem proportions.
    scale = min(190 / src.width, 190 / src.height)
    size = (max(1, round(src.width * scale)), max(1, round(src.height * scale)))
    rgb = src.resize(size, Image.Resampling.LANCZOS)
    a = alpha.resize(size, Image.Resampling.LANCZOS)
    out = Image.new('RGBA', (216, 216), (255,255,255,0))
    x,y = (216-size[0])//2, (216-size[1])//2
    out.paste((255,255,255,255), (x,y), a)
    # Optical centering: the detached lower/side forms make the alpha centroid
    # differ slightly from the rectangular bounds center.
    mass = sx = sy = 0
    for py in range(216):
        for px in range(216):
            weight = out.getpixel((px, py))[3]
            mass += weight; sx += px * weight; sy += py * weight
    dx = round(108 - sx / mass)
    dy = round(108 - sy / mass)
    if dx or dy:
        shifted = Image.new('RGBA', out.size, (255,255,255,0))
        shifted.alpha_composite(out, (dx, dy))
        out = shifted
    return out

def document(image, tag, color):
    if color != 255:
        alpha = image.getchannel('A')
        fill = Image.new('RGBA', image.size, (color,color,color,0)); fill.putalpha(alpha); image = fill
    encoded = 'data:image/png;base64,' + base64.b64encode(png(image)).decode()
    bbox = image.getchannel('A').point(lambda a: 255 if a >= 16 else 0).getbbox()
    bounds = [bbox[0]-108, bbox[1]-108, bbox[2]-108, bbox[3]-108]
    asset = dict(id='frame_0', w=216, h=216, u='', e=1, p=encoded)
    layer = dict(ddd=0, ind=1, ty=2, nm='frame_0', refId='frame_0', sr=1, ip=0, op=90, st=0, bm=0,
                 ks=dict(o=dict(a=0,k=100), r=dict(a=0,k=0), p=dict(a=0,k=[144,144,0]),
                         a=dict(a=0,k=[108,108,0]), s=dict(a=0,k=[100,100,100])))
    return dict(v='5.9.1', fr=120, ip=0, op=90, w=288, h=288, nm=tag, ddd=0,
                assets=[asset], layers=[layer], markers=[], catalogBounds=bounds,
                catalogAlphaThreshold=16, catalogSourceFps=120, catalogFrameStep=1,
                source_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest())

def preview(comp):
    im = Image.open(io.BytesIO(base64.b64decode(comp['assets'][0]['p'].split(',',1)[1]))).convert('RGBA')
    # Render using the same visible-bound normalization as the catalog preview.
    b = comp['catalogBounds']; scale = 112 / max(b[2]-b[0], b[3]-b[1])
    cx, cy = im.width/2 + (b[0]+b[2])/2, im.height/2 + (b[1]+b[3])/2
    return im.transform((144,144), Image.Transform.AFFINE,
                        (1/scale,0,cx-72/scale,0,1/scale,cy-72/scale),
                        resample=Image.Resampling.BICUBIC)

def main():
    global SOURCE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image', nargs='?', type=Path, default=DEFAULT_SOURCE)
    SOURCE = parser.parse_args().image
    shape = extract_shape(); base = document(shape, TAG, 255)
    for name, color in [('pyro',255), ('pyro_light',0), ('pyro_aod',255)]:
        comp = base if name == 'pyro' else document(shape, name, color)
        (CATALOG / f'icons_{name}.json').write_text(json.dumps(comp, separators=(',',':')))
    preview(base).save(PREVIEWS / 'icons_pyro.png', 'PNG', optimize=True)
    index_path = CATALOG / 'index.json'; index = json.loads(index_path.read_text())
    if not any(e['tag'] == TAG for e in index['icons']):
        index['icons'].append({'tag': TAG, 'file':'icons_pyro.json', 'frames':1,
            'source':['user attachment: pyro emblem'], 'source_sha256':base['source_sha256'],
            'variants':['light','aod'], 'style_label':'White Pyro'})
    index_path.write_text(json.dumps(index, indent=2) + '\n')
    print('IMPORTED=icons:pyro')

if __name__ == '__main__': main()
