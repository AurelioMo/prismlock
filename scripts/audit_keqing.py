"""Inspect every PNG and compare the 30fps/60fps/video renditions without executing the overlay."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import zipfile
import numpy as np
from PIL import Image
from import_keqing import SOURCE_SHA256


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    digest = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        raise ValueError('Unexpected source APK')
    args.output.mkdir(parents=True, exist_ok=True)
    entries, families = [], {}
    with zipfile.ZipFile(args.apk) as archive:
        members = archive.namelist()
        for name in members:
            data = archive.read(name)
            entry = dict(member=name, bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
            if name.endswith('.png'):
                image = Image.open(io.BytesIO(data)).convert('RGBA')
                alpha = image.getchannel('A')
                entry.update(width=image.width, height=image.height, alpha_extrema=alpha.getextrema(), alpha_bounds=alpha.getbbox())
                match = re.fullmatch(r'(kgd_fingerprint_element_(?:60fps_)?)(\d+)\.png', Path(name).name)
                if match:
                    pixels = np.asarray(image.resize((64, 64), Image.Resampling.LANCZOS), dtype=np.float32) / 255
                    pixels[:, :, :3] *= pixels[:, :, 3:4]
                    families.setdefault(match[1], {})[int(match[2])] = pixels
            entries.append(entry)
        video = args.output / 'anim.mp4'
        if not video.exists():
            video.write_bytes(archive.read('res/raw/anim.mp4'))
    slow, fast = families['kgd_fingerprint_element_'], families['kgd_fingerprint_element_60fps_']
    alignment = []
    for index, pixels in sorted(slow.items()):
        error, selected = min((float(np.abs(pixels - candidate).mean()), position) for position, candidate in fast.items())
        alignment.append(dict(frame_30=index, closest_frame_60=selected, rgba_mean_error=round(error, 7)))
    video_bytes = subprocess.check_output(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-i', str(video),
            '-map', '0:v:0', '-vf', 'scale=64:64', '-pix_fmt', 'rgb24', '-f', 'rawvideo', 'pipe:1'])
    frames = np.frombuffer(video_bytes, dtype=np.uint8).reshape((-1, 64, 64, 3)).astype(np.float32) / 255
    video_alignment = []
    for i, frame in enumerate(frames):
        error, selected = min((float(np.abs(frame - pixels[:, :, :3]).mean()), position) for position, pixels in fast.items())
        video_alignment.append(dict(video_frame=i, closest_png_frame=selected, rgb_mean_error=round(error, 7)))
    report = dict(apk_sha256=digest, members=len(entries), png_count=sum(e['member'].endswith('.png') for e in entries),
                  dex_count=sum(e['member'].endswith('.dex') for e in entries),
                  library_count=sum(e['member'].endswith('.so') for e in entries), inventory=entries,
                  frame_30_to_60_alignment=alignment, video_frame_count=len(frames), video_to_png_alignment=video_alignment,
                  classification='Resource-only SystemUI overlay; visual extraction only',
                  decision='Use the complete 60fps PNG rendition, static symbol and fade-in/out sequences. Do not add the same 30fps/video design as duplicate rows.',
                  timing_limit='The original fade rate integer is an OEM code, not an explicit Hz declaration; the Samsung port uses 60fps for those 25-frame transitions.')
    (args.output / 'source-apk-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print('SOURCE_MEMBERS=' + str(len(entries)))
    print('SOURCE_PNGS=' + str(report['png_count']))
    print('MEAN_30_TO_60_RGBA_ERROR=' + str(round(sum(e['rgba_mean_error'] for e in alignment) / len(alignment), 7)))
    print('MEAN_VIDEO_TO_PNG_RGB_ERROR=' + str(round(sum(e['rgb_mean_error'] for e in video_alignment) / len(video_alignment), 7)))
    print('RESULT=success')


if __name__ == '__main__':
    main()
