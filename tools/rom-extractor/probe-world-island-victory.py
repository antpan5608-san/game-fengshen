"""Bounded unchanged event7 CPU finalization, not a normal battle or Android run."""
from pathlib import Path
import sys,json,itertools,importlib.util
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from export_development import iteration_reader
from forensics.fengshen246 import digest,extract_npcs
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_call',Path(__file__).with_name('probe-world-village4.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
r=iteration_reader();out=Path('private-derived/world-island-event7-victory');out.mkdir(parents=True,exist_ok=True)
flag=r.word(0,0xd493+76*2);npcs=extract_npcs(r,76)['records'];rows=['mapFlagBefore\tglobal7c6Before\tmapFlagAfter\tglobal7c6After\toverlay\tremainingVillains\tremainingChests\teventAfter'];failures=[]
for old,global6 in itertools.product([0,1,2,32,63,127,128,255],[0,1,16,32,255]):
 c=MPU();c.memory[0x8000:]=r.read(11,0x8000,32768);c.memory[0xe000:]=r.read(0,0xe000,8192)
 c.memory[0xa3]=flag&255;c.memory[0xa4]=flag>>8;c.memory[flag]=old;c.memory[0x7c6]=global6;c.memory[0x47]=76;c.memory[0xa5]=7;c.memory[0xa6]=2;c.memory[0x7d9]=76
 for i,n in enumerate(npcs):
  start=0x418+i*22;c.memory[start:start+14]=bytes.fromhex(n['rawHex'])
 m.call(c,0xceba)
 villains=sum(c.memory[0x418+i*22]!=0 for i in range(4));chests=sum(c.memory[0x418+i*22]==144 for i in range(4,10))
 actual=(c.memory[flag],c.memory[0x7c6],c.memory[0x7d9],villains,chests,c.memory[0xa5]);expected=(old|128,global6|16,199,0,6,0)
 if actual!=expected:failures.append([old,global6,actual,expected])
 rows.append('\t'.join(map(str,(old,global6,*actual))))
raw=('\n'.join(rows)+'\n').encode('ascii');(out/'original-event7-victory-cpu.tsv').write_bytes(raw)
report={'romSha256':digest(r.data),'kind':'CONTROLLED_ORIGINAL_POST_BATTLE_RETURN_CPU_NOT_NORMAL_VICTORY','testCount':len(rows)-1,'failures':failures,'tableSha256':digest(raw),'mapFlagRamAddress':flag,'removedSpriteIds':[162,165,140,143],'overlayContext':199,'commonBattleRewardOnly':True}
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n');print('Original event7 finalization',len(rows)-1,'cases',len(failures),'differences',report['tableSha256'])
if failures:print(failures[:5]);raise SystemExit(1)
