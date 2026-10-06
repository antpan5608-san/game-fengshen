"""Execute original four-slot target selection and actor3 multiplier CPU routines.
Controlled CPU boundaries, not native normal play or Android acceptance.
"""
import argparse,hashlib,importlib.util,itertools,json,sys,functools
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,SHA256
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('shared',Path(__file__).with_name('probe-world-yang-join.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
 p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
 rows=['kind\tinputA\tinputB\tinputC\toutputA\toutputB']
 for alive,byte in itertools.product(range(16),range(256)):
  c=MPU();c.memory[0x8000:]=r.read(9,0x8000,32768);c.memory[0x500]=4;c.memory[0x43]=byte
  for slot in range(4):c.memory[0x12d+slot]=slot+1;c.memory[0x514+2*slot]=int(bool(alive&(1<<slot)))
  c.pc=0x8eb2;c.sp=255;c.stPushWord(0x5fff)
  for _ in range(400):
   if c.pc in (0x6000,0xa60e):break
   c.step()
  else:raise RuntimeError('Original four-party target did not return')
  first=(byte>>2)&3
  expected=next((slot for slot in list(range(first,4))+list(range(first))if alive&(1<<slot)),-1)
  actual=-1 if c.pc==0xa60e else c.memory[0x368]
  assert (actual,c.memory[0x43])==(expected,byte),(alive,byte,actual,expected)
  rows.append('\t'.join(map(str,('four-target',alive,byte,4,actual,c.memory[0x43]))))
 ptr=r.word(9,0x8577+6);table=list(r.read(9,ptr,36))
 for level,byte in itertools.product(list(range(0,60,5))+[79,255],range(256)):
  c=MPU();c.memory[0x8000:]=r.read(9,0x8000,32768);c.memory[0x368]=3;c.memory[0x130]=4;c.memory[0x507]=level;c.memory[0x43]=byte
  shared.call(c,0x8528);bucket=min(min(level,79)//5,11)
  expected=next((i+1 for i in range(3)if byte<table[bucket+12*i]),4)
  assert (c.memory[0x694d],c.memory[0x43])==(expected,byte),(level,byte)
  rows.append('\t'.join(map(str,('four-multiplier',level,byte,3,c.memory[0x694d],c.memory[0x43]))))
 # Same actual fixed eleven-entry scheduler, now with all four actor records.
 scheduler=[]
 for agility,mask in itertools.product(((0,0,0,0),(1,7,7,63),(63,7,7,1),(255,0,128,127)),range(16)):
  c=MPU();c.memory[0x8000:]=r.read(9,0x8000,32768)
  enemy_agility=(0,1,7,7,63,128,255)
  for slot in range(4):c.memory[0x534+slot]=agility[slot];c.memory[0x100+4*slot]=3 if mask&(1<<slot)else 1
  for slot in range(7):c.memory[0x69d4+slot]=enemy_agility[slot]
  # Scheduler is an inline phase, not a callable RTS routine. Stop at its
  # actual next phase after the unchanged original sort has completed.
  c.pc=0x804c
  for _ in range(20000):
   if c.pc==0x8125:break
   c.step()
  else:raise RuntimeError('Original sort did not reach next phase')
  def compare(left,right):
   lp=left<128 and bool(mask&(1<<left));rp=right<128 and bool(mask&(1<<right))
   if lp and rp:return 0
   if lp!=rp:return -1 if lp else 1
   a=agility[left]if left<128 else enemy_agility[left&7];b=agility[right]if right<128 else enemy_agility[right&7]
   return (b>a)-(b<a)
  expected=sorted(list(range(4))+[128+i for i in range(7)],key=functools.cmp_to_key(compare))
  actual=list(c.memory[0x691b:0x6926]);assert actual==expected,(agility,mask,actual,expected)
  scheduler.append(dict(agility=list(agility),priorityMask=mask,enemyAgility=list(enemy_agility),order=actual))
 # Shared original XP division: every HP-positive present slot, including slot3.
 experience=[]
 for alive,total in itertools.product(range(1,16),(0,1,3,4,5,39,255,256,65535)):
  c=MPU();c.memory[0x8000:]=r.read(9,0x8000,32768);c.memory[0x500]=4
  for slot in range(4):c.memory[0x12d+slot]=slot+1;c.memory[0x514+2*slot]=int(bool(alive&(1<<slot)))
  c.memory[0x69b8]=total&255;c.memory[0x69b9]=total>>8
  shared.call(c,0xae12)
  expected=[total//alive.bit_count()if alive&(1<<slot)else 0 for slot in range(4)]
  actual=[sum(c.memory[0x508+3*slot+i]<<(8*i)for i in range(3))for slot in range(4)]
  assert actual==expected,(alive,total,actual,expected)
  experience.append(dict(aliveMask=alive,total=total,shares=actual))
 recovery=[]
 for statuses,byte in itertools.product(((0,0,0,0),(4,8,12,6),(12,4,8,12),(2,16,32,0)),range(256)):
  c=MPU();c.memory[0x8000:]=r.read(9,0x8000,32768);c.memory[0x500]=4;c.memory[0x6926]=10;c.memory[0x43]=byte
  for slot in range(4):c.memory[0x12d+slot]=slot+1;c.memory[0x544+slot]=statuses[slot]
  shared.call(c,0xa69f)
  current=byte;expected=[]
  for status in statuses:
   if status&12:
    current=(current>>1)|128
    if current&1==0:status=0
   expected.append(status)
  actual=list(c.memory[0x544:0x548]);assert (actual,c.memory[0x43])==(expected,current),(statuses,byte,actual,expected)
  recovery.append(dict(statuses=list(statuses),randomBefore=byte,statusesAfter=actual,randomAfter=c.memory[0x43]))
 shared_raw=(json.dumps(dict(scheduler=scheduler,experience=experience,recovery=recovery),sort_keys=True,indent=2)+'\n').encode('ascii')
 csv=lambda values:','.join(map(str,values))
 shared_rows=['kind\tinputA\tinputB\tinputC\toutputA\toutputB']
 shared_rows += ['\t'.join(('scheduler',csv(x['agility']),str(x['priorityMask']),csv(x['enemyAgility']),csv(x['order']),'0'))for x in scheduler]
 shared_rows += ['\t'.join(('experience',str(x['aliveMask']),str(x['total']),'0',csv(x['shares']),'0'))for x in experience]
 shared_rows += ['\t'.join(('recovery',csv(x['statuses']),str(x['randomBefore']),'0',csv(x['statusesAfter']),str(x['randomAfter'])))for x in recovery]
 shared_tsv=('\n'.join(shared_rows)+'\n').encode('ascii')
 a.output.mkdir(parents=True,exist_ok=True);raw=('\n'.join(rows)+'\n').encode('ascii');t=('\t'.join(map(str,table))+'\n').encode('ascii')
 (a.output/'world-jiang-four-party-original.tsv').write_bytes(raw)
 (a.output/'world-jiang-multiplier-original.tsv').write_bytes(t)
 (a.output/'world-jiang-four-party-shared-original.json').write_bytes(shared_raw)
 (a.output/'world-jiang-four-party-shared-original.tsv').write_bytes(shared_tsv)
 sources=[r.span(9,0x8eb2,91,'Original four-slot target dispatch'),r.span(9,0x8528,79,'Original actor3 physical multiplier dispatch'),
  r.span(9,0x8577+6,2,'Original actor3 multiplier pointer'),r.span(9,ptr,36,'Original actor3 multiplier thresholds'),
  r.span(9,0x804c,217,'Original eleven-entry four-actor scheduler'),r.span(9,0xae12,147,'Original present living EXP distribution'),
  r.span(9,0xa69f,73,'Original four-slot sequential status recovery')]
 proof=dict(kind='CONTROLLED_ORIGINAL_CPU_NOT_ANDROID',romSha256=SHA256,cases=len(rows)-1,failures=0,
  targetCases=4096,multiplierCases=3584,schedulerCases=len(scheduler),experienceCases=len(experience),recoveryCases=len(recovery),
  resultsSha256=hashlib.sha256(raw).hexdigest(),multiplierSha256=hashlib.sha256(t).hexdigest(),sharedSha256=hashlib.sha256(shared_raw).hexdigest(),sharedTsvSha256=hashlib.sha256(shared_tsv).hexdigest(),sources=sources,
  limitations=['Fixed bytes, no NMI/timing equivalence','Actual four-person App battle requires separate checks'])
 (a.output/'proof.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof))

if __name__=='__main__':main()
