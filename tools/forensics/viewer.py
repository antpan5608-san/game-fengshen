"""Produce a self-contained local static research viewer; no game/server API."""
import json
import shutil
import webbrowser
from pathlib import Path
from .common import ROOT, save, contained

def build_viewer(raw,reference,comparison,output=None,open_browser=False):
    output=Path(output or ROOT/'reports/map-viewer').resolve();reference=Path(reference).resolve()
    if output.is_relative_to(reference):raise ValueError('Viewer output must not modify reference')
    output.mkdir(parents=True,exist_ok=True);images={};maps=[]
    for m in raw['maps']:
        maps.append({k:v for k,v in m.items() if k!='xmlTree'})
        for ts in m['tilesets']:
            if not ts['image']:continue
            path=ts['image']['path']
            if path in images:continue
            dest='assets/'+Path(path).name;images[path]=dest
            (output/'assets').mkdir(exist_ok=True)
            shutil.copy2(contained(reference,path),contained(output,dest))
    tables={}
    for x in raw['records']:tables.setdefault(x['table'],[]).append(x['data'])
    flags={x['referenceId']:x for x in comparison['nonOriginalContent']['records']}
    data={'maps':maps,'tables':tables,'images':images,'flags':flags,
          'commit':raw['commit'],'databaseSha256':raw['sourceDatabaseSha256']}
    # Script data is external and escaped for safe offline inspection; UI uses textContent.
    (output/'data.js').write_text('window.DATA='+json.dumps(data,ensure_ascii=False,separators=(',',':')).replace('<','\\u003c')+';\n',encoding='utf-8')
    for name in ('index.html','viewer.js','viewer.css'):
        shutil.copy2(ROOT/'tools/map-viewer'/name,output/name)
    save(output/'manifest.json',{'mapCount':len(maps),'imageCount':len(images),'sourceCommit':raw['commit'],
      'purpose':'REFERENCE_RESEARCH_ONLY','originalVerified':False,'transport':'static offline files, no API'})
    path=output/'index.html'
    if open_browser:webbrowser.open(path.as_uri())
    return {'viewer':str(path),'maps':len(maps),'images':len(images),'opened':open_browser}
