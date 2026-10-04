"""Actual category4/id8 chest grant boundary, not a normal-play claim."""
from pathlib import Path
import sys,json,itertools,importlib.util
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from export_development import iteration_reader
from forensics.fengshen246 import digest,extract_npcs
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_call',Path(__file__).with_name('probe-world-village4.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
r=iteration_reader();out=Path('private-derived/world-night8-money');out.mkdir(parents=True,exist_ok=True)
n=extract_npcs(r,74)['records'][4];raw=bytes.fromhex(n['rawHex']);assert list(raw[:3])==[144,4,10]and raw[13]==32
fa=r.word(0,0xd493+74*2);amount=r.word(2,r.word(2,0xe60e+8)+20);assert amount==120
rows=['moneyBefore\tflagBefore\toffered\tamount\tmoneyAfter\tflagAfter'];failures=[]
for old,flag in itertools.product([0,1,1234,999879,999880,999950,999999],[0,1,32,128,255]):
 c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768);c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag
 c.memory[0x674]=1;c.memory[0x605]=3;c.memory[0x6e]=144;c.memory[0x3d]=0x18;c.memory[0x3e]=4;c.memory[0x418:0x426]=raw
 m.call(c,0xa740);offered=c.memory[0x68f]!=0;actualAmount=0
 c.memory[0x501:0x504]=old.to_bytes(3,'little');c.memory[0x8000:]=r.read(2,0x8000,32768)
 if offered:
  c.sp=255;c.stPushWord(0x5fff);c.pc=0x9fb5
  for _ in range(12000):
   if c.pc==0x9fef:break
   c.step()
  else:raise RuntimeError('Original money amount boundary not reached')
  actualAmount=c.memory[0x14]+256*c.memory[0x15]
  c.memory[0x6c1:0x6c4]=actualAmount.to_bytes(3,'little');m.call(c,0xc530)
 after=int.from_bytes(bytes(c.memory[0x501:0x504]),'little');got=(offered,actualAmount,after,c.memory[fa]);expected=(not bool(flag&32),120 if not flag&32 else 0,min(999999,old+120)if not flag&32 else old,flag|32)
 if got!=expected:failures.append([old,flag,got,expected])
 rows.append('\t'.join(map(str,(old,flag,int(offered),actualAmount,after,c.memory[fa]))))
rawTable=('\n'.join(rows)+'\n').encode('ascii');(out/'original-money-grant-cpu.tsv').write_bytes(rawTable)
report=dict(romSha256=digest(r.data),kind='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_ANDROID',testCount=len(rows)-1,failures=failures,tableSha256=digest(rawTable),categoryId=4,originalId=10,amount=120,moneyCap=999999,npcSource=n['range'],amountSource=r.span(2,r.word(2,0xe60e+8)+20,2,'Original category4/id8 money chest amount100'))
(out/'money-report.json').write_text(json.dumps(report,indent=2)+'\n');print('Original money chest',report['testCount'],'cases',len(failures),'differences',report['tableSha256'])
if failures:print(failures[:5]);raise SystemExit(1)
