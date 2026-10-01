"""Phase 0 evidence only. Reads a pinned reference snapshot; never modifies it.

Usage: python audit.py --reference PATH --tree TREE_JSON --out PATH
Pillow is required for complete PNG decode validation.
"""
import argparse
import base64
import collections
import csv
import hashlib
import json
import plistlib
import sqlite3
import struct
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('--reference', type=Path, required=True)
p.add_argument('--tree', type=Path, required=True)
p.add_argument('--out', type=Path, required=True)
a = p.parse_args()
root, out = a.reference.resolve(), a.out.resolve()
out.mkdir(parents=True, exist_ok=True)
tree = json.loads(a.tree.read_text(encoding='utf-8-sig'))
assert not tree.get('truncated'), 'Incomplete GitHub tree'
files = sorted((x for x in tree['tree'] if x['type'] == 'blob'), key=lambda x: x['path'])

def write(name, value):
    (out / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

inventory, failures, images, audio, plists, ccbi = [], [], [], [], [], []
for f in files:
    path = root / f['path']
    if not path.is_file():
        failures.append({'path': f['path'], 'error': 'missing'}); continue
    raw = path.read_bytes()
    blob = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
    item = {'path': f['path'], 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(), 'gitBlobSha1': blob, 'matchesPinnedTree': blob == f['sha']}
    inventory.append(item)
    if not item['matchesPinnedTree']: failures.append({'path': f['path'], 'error': 'blob mismatch'})
    try:
        if path.suffix == '.png':
            with Image.open(path) as im:
                im.load()
                images.append({'path': f['path'], 'width': im.width, 'height': im.height, 'mode': im.mode})
        elif path.suffix == '.plist':
            v = plistlib.loads(raw)
            plists.append({'path': f['path'], 'keys': list(v), 'frameCount': len(v.get('frames', {})), 'metadata': v.get('metadata', {})})
        elif path.suffix == '.ccbi':
            ccbi.append({'path': f['path'], 'header': raw[:4].hex(), 'bytes': len(raw)})
        elif path.suffix == '.mp3':
            audio.append({'path': f['path'], 'bytes': len(raw), 'headerHex': raw[:16].hex(), 'validation': 'header/inventory only; see separate decoder probe'})
    except Exception as e:
        failures.append({'path': f['path'], 'error': str(e)})
write('file-inventory.json', inventory)
write('image-inventory.json', images)
write('audio-inventory.json', audio)
write('plist-inventory.json', plists)
write('ccbi-inventory.json', ccbi)

db = sqlite3.connect((root/'Resources/res/MainData').as_uri()+'?mode=ro', uri=True)
db.row_factory = sqlite3.Row
tables = {r[0]: [dict(x) for x in db.execute('SELECT * FROM "'+r[0]+'"')] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
ids = {k: {r['id'] for r in v} for k,v in tables.items() if v and 'id' in v[0]}
db_info = {}
for name, rows in tables.items():
    columns = [dict(r) for r in db.execute('PRAGMA table_info("'+name+'")')]
    counts = collections.Counter(r.get('id') for r in rows)
    db_info[name] = {'count':len(rows), 'columns':columns, 'duplicateIds':{str(k):v for k,v in counts.items() if k is not None and v>1}, 'nullCounts':{c['name']:sum(r[c['name']] is None for r in rows) for c in columns}, 'foreignKeys':[dict(r) for r in db.execute('PRAGMA foreign_key_list("'+name+'")')]}
write('database-tables.json', db_info)
refs = []
def check(source, column, target, skip=(0,None), predicate=lambda r:True, confidence='structural'):
    bad = [{'rowId':r['id'], 'value':r[column]} for r in tables[source] if predicate(r) and r[column] not in skip and r[column] not in ids[target]]
    refs.append({'source':source, 'column':column, 'target':target, 'ignoredValues':list(skip), 'interpretation':confidence, 'invalid':bad})

for source, columns in {
    'auto_move':{'mapId':'map','npcId':'map_npc','storyId':'story','autoMoveId':'auto_move'},
    'battle':{'map_id':'map','nextStoryId':'story','autoMoveId':'auto_move','mibaoid':'object'},
    'map':{'jump_map_id':'map'}, 'map_box':{'mapId':'map','objId':'object'},
    'map_buy':{'mapId':'map','npcId':'map_npc','objId':'object'}, 'map_mask':{'map_id':'map'},
    'map_npc':{'mapId':'map','storyId':'story','zero_id':'map_npc'},
    'map_object':{'map_id':'map'}, 'map_warp_point':{'cur_map_id':'map','tar_map_id':'map'},
    'monster':{'skillId':'skill','object':'object'}, 'object':{'sell_storyId':'story'},
    'story':{'speakerId':'map_npc','next_story_id':'story','getItemId':'object','autoMoveId':'auto_move'},
    'yingyi':{'mapId':'map','unlock_mapId':'map'},'store':{'connect_id':'object'}
}.items():
    for column, target in columns.items(): check(source,column,target)
for column in [f'monster_{i:02}' for i in range(1,7)]:
    check('map_monster',column,'monster')
    check('battle',column,'monster',predicate=lambda r:r['type']==0,confidence='hypothesis: battle.type=0 direct enemy')
    check('battle',column,'map_monster',predicate=lambda r:r['type']==1,confidence='hypothesis: battle.type=1 encounter pool')
check('story','sceneParam','map',predicate=lambda r:r['nextSceneId']=='2',confidence='hypothesis: nextSceneId=2 map')
check('story','sceneParam','battle',predicate=lambda r:r['nextSceneId']=='3',confidence='hypothesis: nextSceneId=3 battle')
write('reference-checks.json',refs)

maps, map_errors, layer_counts, properties = [], [], collections.Counter(), collections.Counter()
map_warnings = []
image_sizes = {i['path']: (i['width'], i['height']) for i in images}
map_rows = {r['id']:r for r in tables['map']}
for path in sorted((root/'Resources/res/map').glob('*.tmx')):
    record = {'file':path.relative_to(root).as_posix(), 'layers':[], 'tilesets':[]}
    try:
        m=ET.parse(path).getroot();record.update(m.attrib)
        gid_props, ranges = {}, []
        for ts in m.findall('tileset'):
            first=int(ts.get('firstgid')); im=ts.find('image')
            td={'firstgid':first,'name':ts.get('name'),'tileProperties':[]}
            if im is not None:
                td['image']=im.attrib; td['imageExists']=(path.parent/im.get('source')).exists()
                if not td['imageExists']:map_errors.append({'file':record['file'],'error':'missing tileset image','image':im.get('source')})
                actual = image_sizes.get((path.parent/im.get('source')).relative_to(root).as_posix())
                declared = (int(im.get('width')), int(im.get('height')))
                if actual and actual != declared:
                    map_warnings.append({'file':record['file'],'image':im.get('source'),'declared':declared,'actual':actual,'warning':'TMX declared image dimensions differ; preserve declared GID boundaries until reviewed'})
                capacity=(int(im.get('width'))//int(ts.get('tilewidth')))*(int(im.get('height'))//int(ts.get('tileheight')))
                ranges.append((first,first+capacity))
            for t in ts.findall('tile'):
                props={v.get('name'):v.get('value',v.text) for v in t.findall('./properties/property')}
                td['tileProperties'].append({'id':int(t.get('id')),'properties':props})
                gid_props[first+int(t.get('id'))]=props
                properties.update(props.keys())
            record['tilesets'].append(td)
        for layer in m.findall('layer'):
            d=layer.find('data'); enc=d.get('encoding');comp=d.get('compression')
            if enc=='base64':
                b=base64.b64decode(d.text)
                if comp=='zlib':b=zlib.decompress(b)
                elif comp:raise ValueError('unsupported compression '+comp)
                cells=list(struct.unpack('<'+'I'*(len(b)//4),b))
            elif enc=='csv':cells=[int(v) for v in d.text.strip().split(',')]
            else:cells=[int(t.get('gid')) for t in d.findall('tile')]
            expected=int(layer.get('width'))*int(layer.get('height'))
            if len(cells)!=expected:raise ValueError('layer size mismatch')
            gids={g & 0x0fffffff for g in cells if g}
            invalid=sorted(g for g in gids if not any(lo<=g<hi for lo,hi in ranges))
            if invalid:map_errors.append({'file':record['file'],'layer':layer.get('name'),'invalidGids':invalid})
            ld={**layer.attrib,'encoding':enc,'compression':comp,'cells':len(cells),'nonzero':sum(bool(v) for v in cells),'flipCells':sum(bool(v&0xf0000000) for v in cells),'propertiesUsed':[gid_props[g] for g in sorted(gids) if g in gid_props]}
            record['layers'].append(ld);layer_counts.update([layer.get('name')])
        record['objectGroups']=len(m.findall('objectgroup'))
        record['mapProperties']={x.get('name'):x.get('value',x.text) for x in m.findall('./properties/property')}
    except Exception as e:map_errors.append({'file':record['file'],'error':str(e)})
    maps.append(record)
write('map-inventory.json',maps)
write('map-errors.json',map_errors)
write('map-warnings.json',map_warnings)
with (out/'map-catalog.csv').open('w',encoding='utf-8-sig',newline='') as f:
    w=csv.writer(f);w.writerow(['id','name','description','resource','width','height','tileWidth','tileHeight','layers'])
    by_path={r['file'].removeprefix('Resources/res/'):r for r in maps}
    for r in tables['map']:
        m=by_path.get(r['res'],{})
        w.writerow([r['id'],r['name'],r['description'],r['res'],m.get('width'),m.get('height'),m.get('tilewidth'),m.get('tileheight'),','.join(l['name'] for l in m.get('layers',[]))])

# Necessary, not sufficient, reachability: ignore conditions and collisions.
edges=collections.defaultdict(set)
for r in tables['map_warp_point']:
    if r['cur_map_id'] in ids['map'] and r['tar_map_id'] in ids['map']:edges[r['cur_map_id']].add(r['tar_map_id'])
for r in tables['map']:
    if r['jump_map_id'] in ids['map']:edges[r['id']].add(r['jump_map_id'])
visited={1};todo=[1]
while todo:
    for target in edges[todo.pop()]-visited:visited.add(target);todo.append(target)
write('map-graph.json',{'method':'directed warp + jump edges, starts at map 1; ignores flags, collision, script teleport, entry coordinates','edges':{str(k):sorted(v) for k,v in edges.items()},'reachable':sorted(visited),'notReached':sorted(ids['map']-visited)})
growth=[]
for rid in sorted({r['roleId'] for r in tables['role_lv']}):
    rows=[r for r in tables['role_lv'] if r['roleId']==rid];levels=[r['roleLv'] for r in rows]
    growth.append({'roleId':rid,'rows':len(rows),'min':min(levels),'max':max(levels),'gaps':sorted(set(range(min(levels),max(levels)+1))-set(levels))})
write('progression-inventory.json',growth)
summary={'commit':tree['sha'],'filesExpected':len(files),'filesAudited':len(inventory),'bytes':sum(x['bytes'] for x in inventory),'extensions':dict(collections.Counter(Path(x['path']).suffix for x in files)), 'verificationErrors':failures,'pngDecoded':len(images),'audioFiles':len(audio),'plistParsed':len(plists),'ccbiHeaders':dict(collections.Counter(x['header'] for x in ccbi)),'databaseIntegrity':[list(r) for r in db.execute('pragma integrity_check')],'tableCounts':{k:len(v) for k,v in tables.items()},'tmxParsed':len(maps),'mapErrors':len(map_errors),'layers':dict(layer_counts),'tileProperties':dict(properties),'referenceViolations':{r['source']+'.'+r['column']+'->'+r['target']:len(r['invalid']) for r in refs if r['invalid']},'reachableIgnoringConditions':len(visited),'progression':growth}
write('audit-summary.json',summary)
print(json.dumps(summary,ensure_ascii=False,indent=2))
if failures:raise SystemExit(1)
