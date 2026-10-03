"""Bounded original room171 tileset2 foot collision probe, no player state edits."""
import argparse,hashlib,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_map
from py65.devices.mpu6502 import MPU

def call(cpu,address):
    cpu.sp=0xff;cpu.stPushWord(0x5fff);cpu.pc=address
    for _ in range(1000):
        if cpu.pc==0x6000:return
        cpu.step()
    raise RuntimeError('Original room collision did not return')

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    r=Reader(a.rom.read_bytes());m=extract_map(r,171)
    assert m['tilesetId']==2
    bank=r.read(0,0x8000,32768);rows=['source\ttarget\tdirection\tblocked\tplane']
    for source,target,direction in itertools.product([0,1,2],[0,1,2],[1,2,3,4]):
        c=MPU();c.memory[0x8000:]=bank;c.memory[0x71]=2;c.memory[0x99]=source;c.memory[0x98]=target;c.memory[0x97]=direction
        call(c,0xca98);call(c,0xce35)
        rows.append('\t'.join(map(str,(source,target,direction,c.memory[0x9c],c.memory[0x6815]))))
    raw=('\n'.join(rows)+'\n').encode('ascii');a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(raw)
    print('Original room171 cases=36 sha256='+hashlib.sha256(raw).hexdigest())

if __name__=='__main__':main()
