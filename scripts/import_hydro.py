"""Create the white Hydro icon and blue Hydro animation from supplied artwork."""
import argparse, base64, hashlib, io, json, colorsys
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]; CATALOG=ROOT/'app/src/main/assets/catalog'; PREVIEWS=ROOT/'app/src/main/assets/previews'
DEFAULT_SOURCE=Path('/var/folders/7m/vtsn8cg54fv760q8r_y7fkq80000gn/T/codex-clipboard-407dbf5c-9ab4-46b5-b31c-238e770c3a1b.png')

def png(im):
 o=io.BytesIO(); im.save(o,'PNG',optimize=True); return o.getvalue()
def extract_icon(source):
 src=Image.open(source).convert('RGB'); p=src.load(); alpha=Image.new('L',src.size); a=alpha.load()
 for y in range(src.height):
  for x in range(src.width):
   v=max(p[x,y]); a[x,y]=max(0,min(255,int((v-2)*255/24)))
 bbox=alpha.getbbox(); src=src.crop(bbox); alpha=alpha.crop(bbox)
 scale=min(190/src.width,190/src.height); size=(round(src.width*scale),round(src.height*scale)); alpha=alpha.resize(size,Image.Resampling.LANCZOS)
 out=Image.new('RGBA',(216,216),(255,255,255,0)); x,y=(216-size[0])//2,(216-size[1])//2; out.paste((255,255,255,255),(x,y),alpha); return out

def icon_doc(im,name,color=255):
 if color!=255:
  a=im.getchannel('A'); n=Image.new('RGBA',im.size,(color,color,color,0)); n.putalpha(a); im=n
 b=im.getchannel('A').point(lambda x:255 if x>=16 else 0).getbbox(); enc='data:image/png;base64,'+base64.b64encode(png(im)).decode()
 return {'v':'5.9.1','fr':120,'ip':0,'op':90,'w':288,'h':288,'nm':name,'ddd':0,'assets':[{'id':'frame_0','w':216,'h':216,'u':'','e':1,'p':enc}], 'layers':[{'ddd':0,'ind':1,'ty':2,'nm':'frame_0','refId':'frame_0','sr':1,'ip':0,'op':90,'st':0,'bm':0,'ks':{'o':{'a':0,'k':100},'r':{'a':0,'k':0},'p':{'a':0,'k':[144,144,0]},'a':{'a':0,'k':[108,108,0]},'s':{'a':0,'k':[100,100,100]}}}], 'markers':[], 'catalogBounds':[b[0]-108,b[1]-108,b[2]-108,b[3]-108], 'catalogAlphaThreshold':16,'catalogSourceFps':120,'catalogFrameStep':1}
def recolor(im):
 pix=im.load(); out=Image.new('RGBA',im.size); op=out.load()
 for y in range(im.height):
  for x in range(im.width):
   r,g,b,a=pix[x,y]
   if a==0: op[x,y]=(0,0,0,0); continue
   v=max(r,g,b)/255.0; lum=(0.299*r+0.587*g+0.114*b)/255.0
   # Hydro palette: bright cyan highlights, deep ocean shadows.
   t=max(0.0,min(1.0,(lum-0.08)/0.92)); rr=int(8+45*t); gg=int(70+150*t); bb=int(125+125*t)
   op[x,y]=(rr,gg,bb,a)
 return out
def anim_doc(genshin):
 assets=[]; layers=[]; bounds=None
 for i,a in enumerate(genshin['assets']):
  im=Image.open(io.BytesIO(base64.b64decode(a['p'].split(',',1)[1]))).convert('RGBA'); im=recolor(im)
  b=im.getchannel('A').point(lambda x:255 if x>=16 else 0).getbbox()
  if b: bounds=[min(bounds[0],b[0]-144) if bounds else b[0]-144,min(bounds[1],b[1]-144) if bounds else b[1]-144,max(bounds[2],b[2]-144) if bounds else b[2]-144,max(bounds[3],b[3]-144) if bounds else b[3]-144]
  ident=f'frame_{i}'; assets.append({'id':ident,'w':im.width,'h':im.height,'u':'','e':1,'p':'data:image/png;base64,'+base64.b64encode(png(im)).decode()})
  layers.append({'ddd':0,'ind':i+1,'ty':2,'nm':ident,'refId':ident,'sr':1,'ip':i*4,'op':(i+1)*4,'st':0,'bm':0,'ks':{'o':{'a':0,'k':100},'r':{'a':0,'k':0},'p':{'a':0,'k':[144,144,0]},'a':{'a':0,'k':[144,144,0]},'s':{'a':0,'k':[100,100,100]}}})
 return {'v':'5.9.1','fr':120,'ip':0,'op':genshin['op'],'w':288,'h':288,'nm':'hydro','ddd':0,'assets':assets,'layers':layers,'markers':[],'catalogBounds':bounds,'catalogAlphaThreshold':16,'catalogSourceFps':30,'catalogFrameStep':4}
def make_preview(doc):
 a=doc['assets'][len(doc['assets'])//2]; im=Image.open(io.BytesIO(base64.b64decode(a['p'].split(',',1)[1]))).convert('RGBA'); b=doc['catalogBounds']; scale=112/max(b[2]-b[0],b[3]-b[1]); cx,cy=im.width/2+(b[0]+b[2])/2,im.height/2+(b[1]+b[3])/2; return im.transform((144,144),Image.Transform.AFFINE,(1/scale,0,cx-72/scale,0,1/scale,cy-72/scale),resample=Image.Resampling.BICUBIC)
def main():
 global SOURCE
 ap=argparse.ArgumentParser(); ap.add_argument('image',nargs='?',type=Path,default=DEFAULT_SOURCE); SOURCE=ap.parse_args().image
 shape=extract_icon(SOURCE); digest=hashlib.sha256(SOURCE.read_bytes()).hexdigest()
 for name,color in [('hydro',255),('hydro_light',0),('hydro_aod',255)]:
  d=icon_doc(shape,name,color); d['source_sha256']=digest; (CATALOG/f'icons_{name}.json').write_text(json.dumps(d,separators=(',',':')))
 (PREVIEWS/'icons_hydro.png').write_bytes(png(make_preview(icon_doc(shape,'hydro'))))
 genshin=json.loads((CATALOG/'animations_genshin.json').read_text()); anim=anim_doc(genshin); anim['source_sha256']=genshin.get('source_sha256',digest); (CATALOG/'animations_hydro.json').write_text(json.dumps(anim,separators=(',',':'))); (PREVIEWS/'animations_hydro.png').write_bytes(png(make_preview(anim)))
 idx=json.loads((CATALOG/'index.json').read_text())
 if not any(e['tag']=='hydro' for e in idx['icons']): idx['icons'].append({'tag':'hydro','file':'icons_hydro.json','frames':1,'source':['user attachment: hydro emblem'],'source_sha256':digest,'variants':['light','aod'],'style_label':'White Hydro'})
 if not any(e['tag']=='hydro' for e in idx['animations']): idx['animations'].append({'tag':'hydro','file':'animations_hydro.json','frames':24,'source':['derived from animations_genshin.json'],'source_sha256':anim['source_sha256'],'style_label':'Hydro'})
 (CATALOG/'index.json').write_text(json.dumps(idx,indent=2)+'\n')
 print('IMPORTED=hydro')
if __name__=='__main__': main()
