"""Original city guards and Panxi post-text, isolated CPU numerical evidence."""
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
 assert r.word(10,0xcb08+88)==0xcd2d
 address=r.word(0,0xd493+242);rows=['index\tflagBefore\tcure\tmessageBefore\tmessageAfter\tflagAfter']
 for i,flag,cure in itertools.product(range(3),(0,1,2,4,7,128,255),(0,1,128,255)):
  first=(5,10,11)[i];repeat=(6,13,6)[i];mask=1<<i;msg=repeat if flag&mask else first
  c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768);c.memory[address]=flag
  c.memory[0xa3]=address&255;c.memory[0xa4]=address>>8;c.memory[0xa2]=mask
  c.memory[0x7c9]=cure;c.memory[0x3b]=msg;c.memory[0xa1]=44;c.memory[0x7c2]=44
  shared.call(c,0xcd2d)
  expected=(msg+1,flag|mask)if cure and not flag&mask else(msg,flag)
  assert(c.memory[0x3b],c.memory[address])==expected
  assert c.memory[0x7c2]==0 and c.memory[0x7c9]==cure
  rows.append('\t'.join(map(str,(i,flag,cure,msg,*expected))))
 raw=('\n'.join(rows)+'\n').encode('ascii');a.output.mkdir(parents=True,exist_ok=True)
 (a.output/'world-jiang-guard-selector-original.tsv').write_bytes(raw)
 print('guards',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
 # The observed write is at D6F0 after the immediate LDA6/STA3B.
 assert r.read(10,0xd6ec,8).hex()=='a906853b207dd360'
 print('panxi post text',r.span(10,0xd6ec,8,'Original action61 second dialogue6'))

if __name__=='__main__':main()
