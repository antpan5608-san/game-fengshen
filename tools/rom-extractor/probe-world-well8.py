"""Scoped original potion dispatch, consumption, and event20 stages.

Emits derived numerical expectations only. Map8 normal route/Android execution
are separate; the native coordinate predicate itself does not inspect map ID.
"""
import argparse,hashlib,importlib.util,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,SHA256
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)
def main():
 p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
 def save(name,rows):
  a.output.mkdir(parents=True,exist_ok=True);raw=('\n'.join(rows)+'\n').encode('ascii');(a.output/name).write_bytes(raw);print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
 rows=['map\tx\ty\tdirection\tactor\tapplied\tevent\tglobal7c9']
 cases=[(mid,13,26,1,0)for mid in range(256)]+[(mid,x,y,d,actor)for mid,x,y,d,actor in itertools.product((8,16,89),range(12,15),range(25,28),range(1,5),(0,130))]
 for mid,x,y,d,actor in cases:
  c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768)
  for addr,v in [(0x6db,1),(0x47,mid),(7,x),(10,y),(0x6c,d),(0x6e,actor),(0x7c9,0)]:c.memory[addr]=v
  c.pc=0xe2a6
  for _ in range(20):
   if c.pc in (0xe55b,0xe2c5):break
   c.step()
  else:raise RuntimeError('Original well dispatch did not finish')
  applied=int(c.pc==0xe55b);assert(applied,c.memory[0xa5],c.memory[0x7c9])==((1,20,1)if(x,y)==(13,26)else(0,0,0))
  rows.append('\t'.join(map(str,(mid,x,y,d,actor,applied,c.memory[0xa5],c.memory[0x7c9]))))
 save('world-well8-dispatch-original.tsv',rows)
 rows=['quantityBefore\tquantityAfter\tidAfter']
 for qty in (0,1,128,129):
  c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x6d6]=1;c.memory[0x6d7]=1;c.memory[0x580]=1;c.memory[0x5c0]=qty
  shared.call(c,0xa22c);assert(c.memory[0x580],c.memory[0x5c0])==(1,128 if qty&127 else qty)
  rows.append(f'{qty}\t{c.memory[0x5c0]}\t1')
 save('world-well8-consumption-original.tsv',rows)
 assert r.word(11,0xcb5e+40)==0xd2fd and r.word(11,0xc2a6+50)==0xc6d9
 assert r.read(11,0xc6d9,43).hex()=='8000040e040f84050707010580000607041084050007058480000411961f070701050412060700070596ff'
 fa=r.word(0,0xd493+16);assert fa==0x708
 rows=['flagBefore\tglobalBefore\tbeginScript\tbeginContext38\tbeginFlag\tfinishFlag\tfinishGlobal\tfinishEvent']
 for flag,global_flag in itertools.product(range(256),(0,1,7,255)):
  c=MPU();c.memory[0x8000:]=r.read(11,0x8000,32768);c.memory[0xe000:]=r.read(0,0xe000,8192)
  c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag;c.memory[0x7c9]=global_flag;c.memory[0xa5]=20
  shared.call(c,0xd306);assert(c.memory[0x5c],c.memory[0x7d5],c.memory[fa],c.memory[0x7c9])==(25,229,flag,global_flag)
  c.memory[0x5c]=0;shared.call(c,0xcd9a)
  assert(c.memory[fa],c.memory[0x7c9],c.memory[0xa5])==(flag|128,global_flag,0)
  rows.append('\t'.join(map(str,(flag,global_flag,25,229,flag,c.memory[fa],c.memory[0x7c9],c.memory[0xa5]))))
 save('world-well8-event-original.tsv',rows)
if __name__=='__main__':main()
