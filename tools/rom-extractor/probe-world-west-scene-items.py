"""Scoped item0/event9 and item14/event23 CPU effects and bookkeeping.

Outputs derived numerical rows only. Controlled native menu/script evidence
and actual Android play are separate; no ROM, RAM or replay is exported.
"""
import argparse,hashlib,importlib.util,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    def save(name,rows):
        raw=('\n'.join(rows)+'\n').encode('ascii');(a.output/name).write_bytes(raw)
        print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
    rows=['item\tmap\ttargetActor\tyangStatusBefore\tapplied\tyangStatusAfter\tyangHpAfter\tyangMpAfter\tshipAfter\tevent']
    for ident,mid,actor,status in itertools.product((0,14),range(256),(0,130,131,162),(0,1,2,4,32,64,96,255)):
        c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x6db]=ident;c.memory[0x47]=mid;c.memory[0x6e]=actor
        c.memory[0x546]=status;c.memory[0x518]=27;c.memory[0x520]=239;c.memory[0x521]=1;c.memory[0x528]=0;c.memory[0x530]=54
        c.pc=0xe2c5 if ident==0 else 0xe3c9
        success=0xe55b;failure=0xe2fa if ident==0 else 0xe3e8
        for _ in range(50):
            if c.pc in (success,failure):break
            c.step()
        else:raise RuntimeError('Original scene-item dispatch did not finish')
        applied=int(c.pc==success);expected=int((mid,actor)==((37,130)if ident==0 else(42,162)))
        got=(applied,c.memory[0x546],c.memory[0x518]+256*c.memory[0x519],c.memory[0x528],c.memory[0x6812],c.memory[0xa5])
        expected_row=(expected,0 if expected and ident==0 else status,495 if expected and ident==0 else 27,
                      54 if expected and ident==0 else 0,1 if expected and ident==14 else 0,(9 if ident==0 else 23)if expected else 0)
        assert got==expected_row,(ident,mid,actor,status,got,expected_row)
        assert(c.memory[0x520],c.memory[0x521],c.memory[0x530])==(239,1,54)
        rows.append('\t'.join(map(str,(ident,mid,actor,status,*got))))
    save('world-west-scene-items-original.tsv',rows)
    rows=['item\tquantityBefore\tquantityAfter\tidAfter']
    for ident,qty in itertools.product((0,14),(0,1,128,129)):
        c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768)
        c.memory[0x6d6]=1;c.memory[0x6d7]=ident;c.memory[0x580]=ident;c.memory[0x5c0]=qty
        shared.call(c,0xa22c)
        expected=128 if qty&127 else qty
        assert(c.memory[0x580],c.memory[0x5c0])==(ident,expected)
        rows.append('\t'.join(map(str,(ident,qty,c.memory[0x5c0],c.memory[0x580]))))
    save('world-west-scene-item-consumption-original.tsv',rows)

if __name__=='__main__':main()
