"""Scoped original Firecloud NPC flag/gift and existing tileset3 matrix.
Only derived CPU rows are emitted. Not a normal original or Android route.
"""
import argparse,hashlib,itertools,sys,importlib.util
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,SHA256,extract_npcs,extract_map
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_call',Path(__file__).with_name('probe-world-village4.py'));shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)
def main():
 p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
 record=bytes.fromhex(extract_npcs(r,89)['records'][0]['rawHex']);assert(record[0],record[1],record[2],record[12],record[13])==(174,0,1,1,1)
 fa=r.word(0,0xd493+178);rows=['flagBefore\tmessage\tflagAfter']
 for flag in range(256):
  c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768);c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag;c.memory[0xa2]=1;c.memory[0x400:0x40e]=record;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
  for _ in range(80):
   if c.pc==0xa18a:break
   c.step()
  else:raise RuntimeError('Original sage selector did not complete')
  if c.memory[0xa1]==1:shared.call(c,0xcb84)
  expected=(1,flag)if flag&1 else(0,flag|1);assert(c.memory[0x3b],c.memory[fa])==expected
  rows.append('\t'.join(map(str,(flag,*expected))))
 def save(name,rows):
  raw=('\n'.join(rows)+'\n').encode('ascii');a.output.mkdir(parents=True,exist_ok=True);(a.output/name).write_bytes(raw);print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
 save('world-sages89-selector-original.tsv',rows)
 cases={'empty':[],'existing':[(1,1)],'used-positive':[(1,129)],'used-zero':[(1,128)],'used-snow':[(0,128),(11,129)],'full':[(i,1)for i in range(2,18)],'one-free':[(i,1)for i in range(2,17)]}
 rows=['message\tcase\tpotionQtyByte\tgrantFailed\tfirstRowId\tfirstRowQtyByte']
 for msg,case in itertools.product((0,1),cases):
  c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x675]=89;c.memory[0x3b]=msg
  for i,(ident,q)in enumerate(cases[case]):c.memory[0x580+i]=ident;c.memory[0x5c0+i]=q
  shared.call(c,0xb481)
  qty=next((c.memory[0x5c0+i]for i in range(16)if c.memory[0x580+i]==1 and c.memory[0x5c0+i]),0)
  old=next((q for ident,q in cases[case]if ident==1),0)
  expected=(1 if case in ('empty','used-zero','used-snow','one-free')else old)if msg==0 else old
  assert qty==expected,(msg,case,qty,expected)
  assert c.memory[0x5c1]==129 if case=='used-snow'else True
  rows.append('\t'.join(map(str,(msg,case,qty,c.memory[0x68e],c.memory[0x580],c.memory[0x5c0]))))
 save('world-sages89-gift-original.tsv',rows)
 m=extract_map(r,89);assert m['tilesetId']==3
 rows=['map\tmodeBefore\tsource\ttarget\tdirection\tblocked\tmodeAfter']
 for mode,source,target,direction in itertools.product((0,1),(0,1,4,8),(0,1,4,8),range(1,5)):
  c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768)
  for addr,value in [(0x47,89),(0x71,3),(0x99,source),(0x98,target),(0x97,direction),(0x6815,mode)]:c.memory[addr]=value
  shared.call(c,0xca98);shared.call(c,0xce35)
  expected=(1,1)if mode else(int(target==1 or source==4 and direction in (1,3)or source==8 and direction in (1,4)),0)
  actual=(int(c.memory[0x9c]!=0),c.memory[0x6815]);assert actual==expected,(mode,source,target,direction,actual,expected)
  rows.append('\t'.join(map(str,(89,mode,source,target,direction,*actual))))
 save('world-sages89-terrain-original.tsv',rows)
if __name__=='__main__':main()
