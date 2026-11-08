"""Optimize the pink-screen Hailuo Dendro export for PrismLock."""
import argparse,base64,hashlib,io,json,math
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]; CATALOG=ROOT/'app/src/main/assets/catalog'; PREVIEWS=ROOT/'app/src/main/assets/previews'
def png(im):
 o=io.BytesIO(); im.save(o,'PNG',optimize=True); return o.getvalue()
def main():
 ap=argparse.ArgumentParser(); ap.add_argument('source',type=Path); src=ap.parse_args().source; raw=json.loads(src.read_text()); indices=[round(i*35/23) for i in range(24)]; frames=[]; bounds=None
 for i in indices:
  a=raw['assets'][i]; im=Image.open(io.BytesIO(base64.b64decode(a['p'].split(',')[1]))).convert('RGB')
  # Keep the square sensor area from the portrait export.
  im=im.crop((0,140,360,500)); px=im.load()
  for y in range(im.height):
   for x in range(im.width):
    r,g,b=px[x,y]; distance=math.sqrt((r-218)**2+(g-26)**2+(b-130)**2); alpha=0 if distance<35 else min(255,int((distance-35)*4)); px[x,y]=(r,g,b,alpha)
  im=im.convert('RGBA').resize((230,230),Image.Resampling.LANCZOS); framed=Image.new('RGBA',(288,288),(0,0,0,0)); framed.alpha_composite(im,(29,29)); im=framed
  visible=im.getchannel('A').point(lambda v:255 if v>=16 else 0).getbbox(); box=[visible[0]-144,visible[1]-144,visible[2]-144,visible[3]-144] if visible else [-1,-1,1,1]
  bounds=box if bounds is None else [min(bounds[0],box[0]),min(bounds[1],box[1]),max(bounds[2],box[2]),max(bounds[3],box[3])]
  frames.append((f'frame_{len(frames)}',im))
 assets=[{'id':n,'w':288,'h':288,'u':'','e':1,'p':'data:image/png;base64,'+base64.b64encode(png(im)).decode()} for n,im in frames]
 layers=[{'ddd':0,'ind':i+1,'ty':2,'nm':n,'refId':n,'sr':1,'ip':i*4,'op':(i+1)*4,'st':0,'bm':0,'ks':{'o':{'a':0,'k':100},'r':{'a':0,'k':0},'p':{'a':0,'k':[144,144,0]},'a':{'a':0,'k':[144,144,0]},'s':{'a':0,'k':[100,100,100]}}} for i,(n,_) in enumerate(frames)]
 doc={'v':'5.9.1','fr':120,'ip':0,'op':96,'w':288,'h':288,'nm':'dendro','ddd':0,'assets':assets,'layers':layers,'markers':[],'catalogBounds':bounds,'catalogAlphaThreshold':16,'catalogSourceFps':30,'catalogFrameStep':4,'source_sha256':hashlib.sha256(src.read_bytes()).hexdigest()}
 (CATALOG/'animations_dendro.json').write_text(json.dumps(doc,separators=(',',':'))); frames[12][1].save(PREVIEWS/'animations_dendro.png','PNG',optimize=True)
 idx=json.loads((CATALOG/'index.json').read_text());
 if not any(e['tag']=='dendro' for e in idx['animations']): idx['animations'].append({'tag':'dendro','file':'animations_dendro.json','frames':24,'source':['user-supplied Hailuo Dendro Lottie export'],'source_sha256':doc['source_sha256'],'style_label':'Dendro'})
 (CATALOG/'index.json').write_text(json.dumps(idx,indent=2)+'\n'); print('IMPORTED=animations:dendro frames=24 duration=0.8s')
if __name__=='__main__': main()
