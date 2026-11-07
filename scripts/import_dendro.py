"""Create the centered white Dendro fingerprint icon from a supplied emblem."""
import argparse,base64,hashlib,io,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]; CATALOG=ROOT/'app/src/main/assets/catalog'; PREVIEWS=ROOT/'app/src/main/assets/previews'
DEFAULT=Path('/var/folders/7m/vtsn8cg54fv760q8r_y7fkq80000gn/T/codex-clipboard-e3609ef3-ee49-42e6-bf4e-b8bffd05afde.png')
def png(im):
 o=io.BytesIO(); im.save(o,'PNG',optimize=True); return o.getvalue()
def extract(src):
 im=Image.open(src).convert('RGB'); p=im.load(); a=Image.new('L',im.size); ap=a.load()
 for y in range(im.height):
  for x in range(im.width):
   v=max(p[x,y]); ap[x,y]=255 if v>=18 else 0
 b=a.getbbox(); im=im.crop(b); a=a.crop(b); scale=min(190/im.width,190/im.height); size=(round(im.width*scale),round(im.height*scale)); a=a.resize(size,Image.Resampling.LANCZOS)
 out=Image.new('RGBA',(216,216),(255,255,255,0)); x,y=(216-size[0])//2,(216-size[1])//2; out.paste((255,255,255,255),(x,y),a)
 mass=sx=sy=0
 for py in range(216):
  for px in range(216):
   w=out.getpixel((px,py))[3]; mass+=w; sx+=px*w; sy+=py*w
 dx=round(108-sx/mass); dy=round(108-sy/mass)
 if dx or dy:
  shifted=Image.new('RGBA',out.size,(255,255,255,0)); shifted.alpha_composite(out,(dx,dy)); out=shifted
 return out
def doc(im,name,color):
 if color!=255:
  a=im.getchannel('A'); x=Image.new('RGBA',im.size,(color,color,color,0)); x.putalpha(a); im=x
 b=im.getchannel('A').point(lambda v:255 if v>=16 else 0).getbbox(); enc='data:image/png;base64,'+base64.b64encode(png(im)).decode()
 return {'v':'5.9.1','fr':120,'ip':0,'op':90,'w':288,'h':288,'nm':name,'ddd':0,'assets':[{'id':'frame_0','w':216,'h':216,'u':'','e':1,'p':enc}],'layers':[{'ddd':0,'ind':1,'ty':2,'nm':'frame_0','refId':'frame_0','sr':1,'ip':0,'op':90,'st':0,'bm':0,'ks':{'o':{'a':0,'k':100},'r':{'a':0,'k':0},'p':{'a':0,'k':[144,144,0]},'a':{'a':0,'k':[108,108,0]},'s':{'a':0,'k':[100,100,100]}}}], 'markers':[],'catalogBounds':[b[0]-108,b[1]-108,b[2]-108,b[3]-108],'catalogAlphaThreshold':16,'catalogSourceFps':120,'catalogFrameStep':1}
def main():
 ap=argparse.ArgumentParser(); ap.add_argument('source',nargs='?',type=Path,default=DEFAULT); src=ap.parse_args().source; im=extract(src); digest=hashlib.sha256(src.read_bytes()).hexdigest()
 for n,c in [('dendro',255),('dendro_light',0),('dendro_aod',255)]:
  d=doc(im,n,c); d['source_sha256']=digest; (CATALOG/f'icons_{n}.json').write_text(json.dumps(d,separators=(',',':')))
 (PREVIEWS/'icons_dendro.png').write_bytes(png(im))
 idx=json.loads((CATALOG/'index.json').read_text())
 if not any(e['tag']=='dendro' for e in idx['icons']): idx['icons'].append({'tag':'dendro','file':'icons_dendro.json','frames':1,'source':['user attachment: dendro emblem'],'source_sha256':digest,'variants':['light','aod'],'style_label':'White Dendro'})
 (CATALOG/'index.json').write_text(json.dumps(idx,indent=2)+'\n')
 print('IMPORTED=icons:dendro')
if __name__=='__main__': main()
