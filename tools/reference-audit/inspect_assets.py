"""Probe all audio/font files and test exact 5x pixel replication of map sheets.
Requires Pillow, mutagen and fonttools; does not modify reference assets.
"""
import json
import sys
from pathlib import Path
from PIL import Image, ImageChops
from mutagen.mp3 import MP3
from fontTools.ttLib import TTFont

root=Path(sys.argv[1]).resolve(); out=Path(sys.argv[2]);result={'audio':[],'fonts':[],'mapPixelReplication':[]}
for p in sorted(root.rglob('*.mp3')):
    row={'path':p.relative_to(root).as_posix()}
    try:
        m=MP3(p);row.update(seconds=m.info.length,sampleRate=m.info.sample_rate,channels=m.info.channels,bitrate=m.info.bitrate)
    except Exception as e:row['error']=str(e)
    result['audio'].append(row)
for p in sorted(root.rglob('*.ttf')):
    f=TTFont(p);cmap=f.getBestCmap();names=f['name'].names
    result['fonts'].append({'path':p.relative_to(root).as_posix(),'glyphs':len(cmap),'cjkCodepoints':sum(0x4e00<=k<=0x9fff for k in cmap),'names':list(dict.fromkeys(n.toUnicode() for n in names if n.nameID in (0,1,2,13,14)))})
for p in sorted((root/'Resources/res/map').glob('*.png')):
    im=Image.open(p).convert('RGBA');row={'path':p.relative_to(root).as_posix(),'width':im.width,'height':im.height,'exact5x':False}
    if im.width%5==0 and im.height%5==0:
        small=im.resize((im.width//5,im.height//5),Image.Resampling.NEAREST)
        row['exact5x']=ImageChops.difference(im,small.resize(im.size,Image.Resampling.NEAREST)).getbbox(alpha_only=False) is None
    result['mapPixelReplication'].append(row)
out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'audio':len(result['audio']),'audioErrors':[x for x in result['audio'] if 'error' in x],'totalSeconds':sum(x.get('seconds',0) for x in result['audio']),'fonts':result['fonts'],'mapImages':len(result['mapPixelReplication']),'exact5x':sum(x['exact5x'] for x in result['mapPixelReplication'])},ensure_ascii=False))
