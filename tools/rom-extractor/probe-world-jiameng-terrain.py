"""Run the existing two-plane CPU matrix for the four current Jiameng grids.

Only the batch IDs/output directory change; no ROM or movement code is patched.
Keeping the historical probe bytes unchanged preserves existing recipe pins.
"""
from pathlib import Path
import hashlib, json
import itertools, collections

source=Path('tools/rom-extractor/probe-world-island-terrain.py')
raw=source.read_bytes()
assert hashlib.sha256(raw).hexdigest()=='f90c4f00e71d99e2ce3cf54aebfbe6a29f2f2d7a565a00bc2b5316117786374a'
text=raw.decode('utf-8')
changes={
    "out=Path('private-derived/world-island76-through78-terrain')":
        "out=Path('private-derived/world-jiameng145-through148-terrain')",
    'for mid in [76,77,78]:':'for mid in [145,146,147,148]:',
}
for before,after in changes.items():
    assert text.count(before)==1,before
    text=text.replace(before,after)
scope={'__name__':'__main__','__file__':str(source)}
exec(compile(text,str(source),'exec'),scope)
out=scope['out'];report=json.loads((out/'report.json').read_text(encoding='utf-8'))
report['scope']='JIAMENG_GRID_BATCH_NOT_ANDROID_ROUTE'
report['executedProbeSha256']=hashlib.sha256(raw).hexdigest()
report['scopeReplacements']=changes
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('Original Jiameng grids:',report['testCount'],'cases;',len(report['failures']),'differences')

# The original post-script room uses the existing tileset2 CPU branch; compare
# its actual class domain rather than applying the cave profile to that room.
reader=scope['r'];original=scope['extract_map'](reader,37)
assert original['tilesetId']==2
collision=original['collisionCandidate'];lookup=reader.read(collision['module'],collision['cpuAddress'],256)
counts=dict(sorted(collections.Counter(str(lookup[t])for row in original['grid']for t in row).items()))
rows=['source\ttarget\tdirection\tblocked\tplane'];differences=[]
for source_class,target_class,direction in itertools.product(sorted(map(int,counts)),sorted(map(int,counts)),range(1,5)):
    cpu=scope['MPU']();cpu.memory[0x8000:]=scope['bank']
    for address,value in [(0x47,37),(0x71,2),(0x99,source_class),(0x98,target_class),(0x97,direction)]:
        cpu.memory[address]=value
    scope['module'].call(cpu,0xca98);scope['module'].call(cpu,0xce35)
    result=(int(cpu.memory[0x9c]!=0),cpu.memory[0x6815])
    if result!=(int(target_class==1),0):differences.append([source_class,target_class,direction,result])
    rows.append('\t'.join(map(str,(source_class,target_class,direction,*result))))
raw=('\n'.join(rows)+'\n').encode('ascii');(out/'room37-collision-original-cpu.tsv').write_bytes(raw)
room=dict(mapId=37,gridSha256=original['gridSha256'],classCounts=counts,testCount=len(rows)-1,
    differences=differences,tableSha256=hashlib.sha256(raw).hexdigest())
(out/'room37-report.json').write_text(json.dumps(room,indent=2)+'\n',encoding='utf-8')
print('Original room37:',room['testCount'],'cases;',len(differences),'differences')
assert not differences,differences[:8]
