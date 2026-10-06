"""Full original foot-mode tileset6 matrix, reused Scene directional edge model."""
import argparse,hashlib,importlib.util,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,SHA256
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing',Path(__file__).with_name('probe-world-yang-join.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)
def main():
 p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
 rows=['source\ttarget\tdirection\tblocked\tmode\tspriteControl']
 for source,target,direction in itertools.product(range(11),range(11),range(1,5)):
  c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768)
  c.memory[0x6815]=0;c.memory[0x99]=source;c.memory[0x98]=target;c.memory[0x97]=direction;c.memory[0x71]=6
  # Whole source and target dispatch; no direct guessed per-class branch.
  shared.call(c,0xca98);shared.call(c,0xce35)
  rows.append('\t'.join(map(str,(source,target,direction,int(c.memory[0x9c]!=0),c.memory[0x6815],c.memory[0x9b]))))
 raw=('\n'.join(rows)+'\n').encode('ascii');a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(raw)
 print('cases',484,'sha256',hashlib.sha256(raw).hexdigest())
if __name__=='__main__':main()
