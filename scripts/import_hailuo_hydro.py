"""Optimize a Hailuo Hydro Lottie export for PrismLock's 288px/120fps catalog."""
import argparse, base64, hashlib, io, json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]; CATALOG=ROOT/'app/src/main/assets/catalog'; PREVIEWS=ROOT/'app/src/main/assets/previews'
def png(im):
 o=io.BytesIO(); im.save(o,'PNG',optimize=True); return o.getvalue()
def main():
 ap=argparse.ArgumentParser(); ap.add_argument('source',type=Path); args=ap.parse_args(); source=args.source
 raw=json.loads(source.read_text()); frames=[]
 # Cover the build-up, full ripple and fade in 24 frames at 30fps (0.8s).
 indices=[round(i*47/23) for i in range(24)]
 bounds=None
 for i in indices:
  a=raw['assets'][i]; im=Image.open(io.BytesIO(base64.b64decode(a['p'].split(',',1)[1]))).convert('RGBA')
  px=im.load()
  for y in range(im.height):
   for x in range(im.width):
    r,g,b,_=px[x,y]; alpha=max(r,g,b)
    # Remove Hailuo's opaque black plate and its low-level compression haze.
    px[x,y]=(r,g,b,alpha if alpha >= 18 else 0)
  im=im.resize((230,230),Image.Resampling.LANCZOS)
  framed=Image.new('RGBA',(288,288),(0,0,0,0)); framed.alpha_composite(im,(29,29)); im=framed
  visible=im.getchannel('A').point(lambda v:255 if v>=16 else 0).getbbox()
  if visible:
   box=[visible[0]-144,visible[1]-144,visible[2]-144,visible[3]-144]
   bounds=box if bounds is None else [min(bounds[0],box[0]),min(bounds[1],box[1]),max(bounds[2],box[2]),max(bounds[3],box[3])]
  ident=f'frame_{len(frames)}'; frames.append((ident,im))
 assets=[{'id':ident,'w':288,'h':288,'u':'','e':1,'p':'data:image/png;base64,'+base64.b64encode(png(im)).decode()} for ident,im in frames]
 layers=[]
 for i,(ident,_) in enumerate(frames):
  layers.append({'ddd':0,'ind':i+1,'ty':2,'nm':ident,'refId':ident,'sr':1,'ip':i*4,'op':(i+1)*4,'st':0,'bm':0,'ks':{'o':{'a':0,'k':100},'r':{'a':0,'k':0},'p':{'a':0,'k':[144,144,0]},'a':{'a':0,'k':[144,144,0]},'s':{'a':0,'k':[100,100,100]}}})
 doc={'v':'5.9.1','fr':120,'ip':0,'op':96,'w':288,'h':288,'nm':'hydro','ddd':0,'assets':assets,'layers':layers,'markers':[],'catalogBounds':bounds,'catalogAlphaThreshold':16,'catalogSourceFps':30,'catalogFrameStep':4,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest()}
 (CATALOG/'animations_hydro.json').write_text(json.dumps(doc,separators=(',',':')))
 mid=frames[len(frames)//2][1]; mid.save(PREVIEWS/'animations_hydro.png','PNG',optimize=True)
 idx=json.loads((CATALOG/'index.json').read_text())
 for e in idx['animations']:
  if e['tag']=='hydro': e['source']=['user-supplied Hailuo Hydro Lottie export']; e['source_sha256']=doc['source_sha256']; e['frames']=24
 (CATALOG/'index.json').write_text(json.dumps(idx,indent=2)+'\n')
 print('IMPORTED=animations:hydro frames=24 duration=0.8s')
if __name__=='__main__': main()
