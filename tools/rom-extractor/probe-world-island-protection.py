"""Actual battle protection branch and item9 effect, bounded original CPU fixtures."""
from pathlib import Path
import sys,itertools,json
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from export_development import iteration_reader
from forensics.fengshen246 import digest
from py65.devices.mpu6502 import MPU
r=iteration_reader();bank=r.read(9,0x8000,32768);out=Path('private-derived/world-island-binding');out.mkdir(parents=True,exist_ok=True)
ids=list(r.read(9,0xaba8,13));flags=list(r.read(9,0xac25,13));assert ids[:4]==[152,153,154,155] and flags[:4]==[1]*4
assert r.word(9,0x890a+18)==0x8935
rows=['enemy\tmarker\tdamageBefore\tdamageAfter'];failures=0
for enemy,marker,damage in itertools.product(ids[:4],[0,1,2,255],[0,1,64,1000,65535]):
 c=MPU();c.memory[0x8000:]=bank;c.y=ids.index(enemy);c.memory[0x6948]=marker
 c.memory[0x14]=damage&255;c.memory[0x15]=damage>>8;c.pc=0xac32
 for _ in range(20):
  if c.pc in [0xac43,0xadc6]:break
  c.step()
 else:raise RuntimeError('Original damage gate did not reach its original boundary')
 got=c.memory[0x14]+256*c.memory[0x15];expected=damage if marker==1 else 0;failures+=got!=expected
 rows.append('\t'.join(map(str,(enemy,marker,damage,got))))
raw=('\n'.join(rows)+'\n').encode();(out/'original-damage-gate.tsv').write_bytes(raw)
rows2=['enemy\tmarkerBefore\tmarkerAfter'];effectFailures=0
for enemy,marker in itertools.product([0,46,151,152,153,154,155,156],[0,1,2,255]):
 c=MPU();c.memory[0x8000:]=bank;c.memory[0x0367]=0;c.memory[0x6977]=enemy;c.memory[0x6948]=marker;c.pc=0x8935
 for _ in range(40):
  if c.memory[c.pc]==0x60:break
  c.step()
 else:raise RuntimeError('Original item9 effect did not return')
 expected=1 if enemy in ids[:4]else marker;got=c.memory[0x6948];effectFailures+=got!=expected
 rows2.append('\t'.join(map(str,(enemy,marker,got))))
raw2=('\n'.join(rows2)+'\n').encode();(out/'original-item9-binding.tsv').write_bytes(raw2)
report=dict(romSha256=digest(r.data),kind='CONTROLLED_ORIGINAL_BRANCH_CPU_NOT_NORMAL_BATTLE',enemyIds=ids[:4],requiredMarker=1,itemOriginalId=9,damageCases=80,damageFailures=failures,damageSha256=digest(raw),effectCases=32,effectFailures=effectFailures,effectSha256=digest(raw2))
(out/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(report)
if failures or effectFailures:raise AssertionError('Original scoped protection/effect differs')
