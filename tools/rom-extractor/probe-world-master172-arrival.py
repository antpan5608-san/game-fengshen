"""Bounded original B9D2/F598 map replacement, controlled CPU only.

Numerical rows are shareable; ROM and raw native captures stay private.
"""
import argparse,hashlib,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,SHA256
from py65.devices.mpu6502 import MPU

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
    rows=['requestedMap\tpartyCount\titemId\tquantityByte\trow\tactualMap']
    for mid,party,ident,qty,row in itertools.product((16,101,171,172),(1,2,3,4),(0,1),(0,1,128,129),(0,15)):
        c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768);c.memory[0x47]=mid;c.memory[0x500]=party
        c.memory[0x580+row]=ident;c.memory[0x5c0+row]=qty;c.pc=0xb9d2
        for _ in range(400):
            if c.pc==0xb9f7:break
            c.step()
        else:raise RuntimeError('Original bounded map selector did not complete')
        expected=172 if mid==171 and party<4 and ident==0 and qty&128 else mid
        assert c.memory[0x47]==expected,(mid,party,ident,qty,row,c.memory[0x47],expected)
        assert c.memory[0x580+row]==ident and c.memory[0x5c0+row]==qty
        rows.append('\t'.join(map(str,(mid,party,ident,qty,row,expected))))
    raw=('\n'.join(rows)+'\n').encode('ascii');a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(raw)
    print('cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
if __name__=='__main__':main()
