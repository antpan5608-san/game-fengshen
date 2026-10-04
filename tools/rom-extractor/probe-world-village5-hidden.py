"""Bounded C6 investigation selection and existing category grant; controlled CPU only."""
import argparse,hashlib,importlib.util,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs,SHA256
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('original_chest_cpu',Path(__file__).with_name('probe-world-tree-chests.py'));shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)
def main():
 p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
 raw=bytes.fromhex(extract_npcs(r,5)['records'][5]['rawHex']);assert list(raw[:3])==[198,0,1] and list(raw[12:])==[0,1]
 # The animation transport subroutine alone is intercepted. Actual selector,
 # quantity grant and success-only flag instructions execute from target bytes.
 rows=['case\tdispatch\tcategory\titem\tapplied\tquantity\tmapFlag'];bank=r.read(2,0x8000,32768);failures=0
 for opened in [0,1]:
  cpu=MPU();cpu.memory[0x8000:]=r.read(10,0x8000,32768);cpu.memory[0xa93a]=0x60
  cpu.memory[0x400:0x40e]=raw;cpu.memory[0x3d]=0;cpu.memory[0x3e]=4;cpu.memory[0x6e]=198
  cpu.memory[0x674]=1;cpu.memory[0x605]=3;cpu.memory[0x47]=5;cpu.memory[0xa3]=5;cpu.memory[0xa4]=7;cpu.memory[0x705]=opened
  cpu.sp=0xff;cpu.stPushWord(0x5fff);shared.run(cpu,0xa740,{0x6000})
  dispatch=cpu.memory[0x18];active=cpu.memory[0x68f]
  failures+=(dispatch,active,cpu.memory[0x705])!=(47 if not opened else 0,int(not opened),opened)
  if not opened:failures+=(cpu.memory[0x6c0],cpu.memory[0x6bf])!=(0,1)
  rows.append(f'select-{opened}\t{dispatch}\t0\t1\t{active}\t0\t{cpu.memory[0x705]}')
 base=r.word(2,0xa194);item=1;limit=10;others=list(range(2,18))
 cases={'empty':[],'one':[(1,1)],'below_cap':[(1,9)],'at_cap':[(1,10)],'used':[(1,129)],'full_new':[(i,1)for i in others],'full_existing':[(1,1)]+[(i,1)for i in others[:15]]}
 for name,initial in cases.items():
  cpu=MPU();cpu.memory[0x8000:]=bank;cpu.memory[0x6d6]=0;cpu.memory[0x6d7]=1
  for j,(i,q)in enumerate(initial):cpu.memory[base+j]=i;cpu.memory[base+64+j]=q
  cpu.sp=0xff;cpu.stPushWord(0x5fff);shared.run(cpu,0xa0eb,{0x6000});applied=int(cpu.memory[0x68e]==0)
  cpu.memory[0xa3]=5;cpu.memory[0xa4]=7;cpu.memory[0x682d]=1;shared.run(cpu,0x9f2c,{0x9f3a,0x9f72})
  qs=[cpu.memory[base+64+j]&127 for j in range(16)if cpu.memory[base+j]==1 and cpu.memory[base+64+j]&127];qty=qs[0]if qs else 0
  prior=next((q&127 for i,q in initial if i==1),0);expected=int(prior<10 and (prior>0 or len(initial)<16))
  failures+=(applied,qty,cpu.memory[0x705])!=(expected,prior+expected,expected)
  rows.append(f'{name}\t47\t0\t1\t{applied}\t{qty}\t{cpu.memory[0x705]}')
 if failures:raise AssertionError(f'{failures} original hidden pickup differences')
 a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(('\n'.join(rows)+'\n').encode('ascii'));print('ORIGINAL_CPU',len(rows)-1,'failures',failures,'sha256',hashlib.sha256(a.output.read_bytes()).hexdigest())
if __name__=='__main__':main()
