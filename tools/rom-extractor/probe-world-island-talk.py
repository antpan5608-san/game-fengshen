"""Original NPC callback31, not unrelated world event31. Derived table only."""
from pathlib import Path
import sys,itertools,importlib.util,json
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from export_development import iteration_reader
from forensics.fengshen246 import extract_npcs,digest
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_call',Path(__file__).with_name('probe-world-village4.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
r=iteration_reader();out=Path('private-derived/world-island-talk31');out.mkdir(parents=True,exist_ok=True)
fa=r.word(0,0xd493+156);rows=['npc\tmask\tflagBefore\tglobal7c6\tmessage\tflagAfter\tevent7c2'];failures=0
for n in extract_npcs(r,78)['records']:
 raw=bytes.fromhex(n['rawHex']);assert(raw[12],raw[13])==(31,1<<n['index'])
 for flag,g in itertools.product(range(256),[0,16]):
  c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768);c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8
  c.memory[fa]=flag;c.memory[0x7c6]=g;c.memory[0x7c2]=0
  c.memory[0x400:0x40e]=raw;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
  for _ in range(80):
   if c.pc==0xa18a:break
   c.step()
  else:raise RuntimeError('Original selector did not finish')
  if c.memory[0xa1]==31:m.call(c,r.word(10,0xcb08+62))
  got=(c.memory[0x3b],c.memory[fa],c.memory[0x7c2])
  expected=(2,flag,0)if flag&raw[13]else((2,flag|raw[13],0)if g else(raw[1],flag,0))
  failures+=got!=expected;rows.append('\t'.join(map(str,(n['index'],raw[13],flag,g,*got))))
raw=('\n'.join(rows)+'\n').encode('ascii');(out/'original-selector-cpu.tsv').write_bytes(raw)
report=dict(romSha256=digest(r.data),kind='CONTROLLED_ORIGINAL_NPC_CALLBACK_NOT_NORMAL_ANDROID',caseCount=len(rows)-1,failures=failures,tableSha256=digest(raw),callbackAddress=r.word(10,0xcb08+62),noWorldEvent31=True)
(out/'selector-report.json').write_text(json.dumps(report,indent=2)+'\n');print(report)
if failures:raise AssertionError('Original callback31 differences')
