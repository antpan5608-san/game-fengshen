"""Original generic boat219 shore dispatch and foot port cells.
Derived numeric expectations, not normal voyage/App evidence. Special Fubing
return bypasses this generic matrix and has separately captured map load costs.
"""
import argparse,hashlib,importlib.util,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
 p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
 rows=['mode\tsource\ttarget\tdirection\tblocked\tmodeAfter\tparkX\tparkY\tparkFlag']
 foot={0,2,15,16,25,26}
 for mode,source,target,direction in itertools.product((0,219),sorted(foot|{4}), (25,26),range(1,5)):
  c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768)
  for address,value in [(0x71,1),(0x47,16),(0x6815,mode),(0x99,source),(0x98,target),(0x97,direction),(0x6c,direction),(0x7a,17),(0x7b,37)]:c.memory[address]=value
  shared.call(c,0xca98);shared.call(c,0xce35)
  legal=(direction<=2 if target==25 else direction>=3)
  if mode==219:
   expected=(0,0,24,44,61)if legal else(1,219,0,0,0)
  else:
   sourceBlocked=source==15 and direction>=3 or source==16 and direction<=2
   expected=(int(sourceBlocked),0,0,0,0)
  got=(int(c.memory[0x9c]!=0),c.memory[0x6815],c.memory[0x681a],c.memory[0x681b],c.memory[0x681f])
  assert got==expected,(mode,source,target,direction,got,expected)
  rows.append('\t'.join(map(str,(mode,source,target,direction,*got))))
 raw=('\n'.join(rows)+'\n').encode();name='world-west-boat-ports-original.tsv';(a.output/name).write_bytes(raw)
 print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())

if __name__=='__main__':main()
