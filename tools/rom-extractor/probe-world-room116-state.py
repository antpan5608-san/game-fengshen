"""Scoped unchanged original room116 actor41/event15. Derived numbers only.

Controlled CPU comparisons are not normal App play or proof of a costume timer.
"""
import argparse,hashlib,importlib.util
from pathlib import Path
from py65.devices.mpu6502 import MPU
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs
spec=importlib.util.spec_from_file_location('existing_call',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
 p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
 r=Reader(args.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()=='f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25'
 args.output.mkdir(parents=True,exist_ok=True)
 assert r.word(11,0xcb5e+30)==0xd236 and r.word(11,0xc2a6+38)==0xc5d8
 assert r.read(11,0xc5d8,9).hex()=='8c00040809070302ff'
 rec=bytes.fromhex(extract_npcs(r,116)['records'][0]['rawHex']);assert (rec[0],rec[1],rec[2],rec[12],rec[13])==(140,6,8,41,1)
 assert r.word(10,0xcb08+82)==0xcd1a
 address=r.word(0,0xd493+116*2)
 def save(name,rows):
  raw=('\n'.join(rows)+'\n').encode();(args.output/name).write_bytes(raw)
  print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest(),flush=True)
 rows=['mapFlagBefore\tmessage\tactionPending\tmapFlagAfter']
 for flag in range(256):
  cpu=MPU();cpu.memory[0x8000:]=r.read(10,0x8000,32768);cpu.memory[0xa3]=address&255;cpu.memory[0xa4]=address>>8;cpu.memory[address]=flag
  cpu.memory[0xa2]=1;cpu.memory[0x400:0x40e]=rec;cpu.memory[0x3d]=0;cpu.memory[0x3e]=4;cpu.pc=0xa160
  for _ in range(80):
   if cpu.pc==0xa18a:break
   cpu.step()
  else:raise RuntimeError('Original actor41 selector did not finish')
  if cpu.memory[0xa1]==41:shared.call(cpu,0xcd1a)
  got=(cpu.memory[0x3b],cpu.memory[0x7c2],cpu.memory[address]);assert got==((8,0,flag)if flag&1 else(6,41,flag|1)),(flag,got)
  rows.append('\t'.join(map(str,(flag,*got))))
 save('room116-actor41-selector-original.tsv',rows)
 cpu=MPU();cpu.memory[0x8000:]=r.read(11,0x8000,32768);shared.call(cpu,0xd23f)
 assert (cpu.memory[0x5c],cpu.memory[0xa6],cpu.memory[0x7e1])==(19,1,209)
 rows=['mapFlagBefore\tglobalBefore\tmapFlagAfter\tglobalAfter\teventAfter']
 for flag in range(256):
  for global_flag in [0,255]:
   cpu=MPU();cpu.memory[0x8000:]=r.read(11,0x8000,32768);cpu.memory[0xe000:]=r.read(0,0xe000,8192)
   cpu.memory[0xa3]=address&255;cpu.memory[0xa4]=address>>8;cpu.memory[address]=flag;cpu.memory[0x7c6]=global_flag;cpu.memory[0xa5]=15
   shared.call(cpu,0xcd9a);got=(cpu.memory[address],cpu.memory[0x7c6],cpu.memory[0xa5]);assert got==(flag|128,global_flag,0)
   rows.append('\t'.join(map(str,(flag,global_flag,*got))))
 save('room116-event15-finish-original.tsv',rows)
if __name__=='__main__':main()
