"""Scoped three-island-floor matrix using existing original CPU call helper; no Android proof."""
from pathlib import Path
import sys,json,collections,itertools,importlib.util
sys.path.insert(0,'tools')
from export_development import iteration_reader
from forensics.fengshen246 import extract_map,digest
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_probe',Path('tools/rom-extractor/probe-world-village4.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
r=iteration_reader();bank=r.read(0,0x8000,32768)
out=Path('private-derived/world-island76-through78-terrain');out.mkdir(parents=True,exist_ok=True)
rows=['# mapId inputMode sourceClass targetClass direction blocked modeAfter originalSpriteControl9b']
maps={};failures=[];upper={10,11,13,14,17,18,23}
blocked={2:{3},3:{4},4:{1,3},5:{2,3},6:{1},7:{2},8:{1,4},9:{2,4},10:{1,2},11:{3,4}}
for mid in [76,77,78]:
    m=extract_map(r,mid);cl=m['collisionCandidate'];lookup=r.read(cl['module'],cl['cpuAddress'],256)
    counts=dict(sorted(collections.Counter(str(lookup[t])for row in m['grid']for t in row).items()));classes=sorted(map(int,counts))
    start=len(rows)
    for mode,source,target,direction in itertools.product([0,1],classes,classes,range(1,5)):
        c=MPU();c.memory[0x8000:]=bank
        for address,value in [(0x47,mid),(0x71,3),(0x99,source),(0x98,target),(0x97,direction),(0x6815,mode)]:c.memory[address]=value
        module.call(c,0xca98);module.call(c,0xce35)
        actual=(int(c.memory[0x9c]!=0),c.memory[0x6815])
        if source==23:expected=(int(direction not in [1,2]),(1 if target==14 else 0)if direction in [1,2]else mode)
        elif mode==1:expected=(int(source not in upper or target not in upper),mode)
        else:expected=(int(direction in blocked.get(source,set())or target in {1,14}),0)
        if actual!=expected:failures.append([mid,mode,source,target,direction,actual,expected])
        rows.append('\t'.join(map(str,(mid,mode,source,target,direction,*actual,c.memory[0x9b]))))
    maps[str(mid)]=dict(mapId=mid,gridSha256=m['gridSha256'],classCounts=counts,testCount=len(rows)-start)
raw=('\n'.join(rows)+'\n').encode('ascii');(out/'world-island-collision-original-cpu.tsv').write_bytes(raw)
report=dict(romSha256=digest(r.data),kind='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_APP',maps=maps,testCount=len(rows)-1,failures=failures,tableSha256=digest(raw),sourcesFrom='Existing world-hell-hall-batch-terrain.json dispatch spans; same original tileset3, current actual map-specific classes')
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n')
print('Original island terrain',report['testCount'],'cases',len(failures),'differences',report['tableSha256'])
if failures:print(failures[:8]);raise SystemExit(1)
