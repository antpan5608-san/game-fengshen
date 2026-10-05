"""Scoped original house dispatch, item-witness talk and plain-room CPU parity.

Derived TSV only. Menu/route evidence remains separate controlled FCEUX input;
this does not claim normal Android play, boat travel or a cure.
"""
import argparse, hashlib, importlib.util, itertools, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader, extract_npcs
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    def save(name,rows):
        raw=('\n'.join(rows)+'\n').encode('ascii');(a.output/name).write_bytes(raw)
        print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
    rows=['caller\thouseIndex\tbusy\ttoMap\tcallerAfter']
    for caller,house,busy in itertools.product(range(16),range(3),(0,1)):
        c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768)
        c.memory[0x47]=caller;c.memory[0x56]=busy;c.memory[0x9d]=99
        shared.call(c,(0xcc33,0xcc3e,0xcc49)[house]);got=(c.memory[0x47],c.memory[0x9d])
        assert got==((caller,99)if busy else(r.read(0,0xd287+3*caller+house)[0],caller))
        rows.append('\t'.join(map(str,(caller,house,busy,*got))))
    save('world-west-house-entry-original.tsv',rows)
    rows=['source\ttarget\tdirection\tblocked']
    for source,target,direction in itertools.product((0,1,2,5),(0,1,2,5),range(1,5)):
        c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768)
        c.memory[0x71]=2
        c.memory[0x99]=source;c.memory[0x98]=target;c.memory[0x97]=direction
        shared.call(c,0xca98);shared.call(c,0xce35)
        got=int(c.memory[0x9c]!=0);assert got==int(target==1)
        rows.append('\t'.join(map(str,(source,target,direction,got))))
    save('world-west-house-terrain-original.tsv',rows)
    rows=['map\tflagBefore\tquantityByte\tmessage\tflagAfter\teventAfter']
    for mid in (41,42):
        raw=bytes.fromhex(extract_npcs(r,mid)['records'][0]['rawHex']);fa=r.word(0,0xd493+2*mid)
        assert (raw[12],raw[13])==((55,1)if mid==41 else(56,2))
        for flag,qty in itertools.product(range(256),(0,1,128,129)):
            c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768)
            c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag;c.memory[0xa2]=raw[13]
            c.memory[0x580]=14;c.memory[0x5c0]=qty;c.memory[0x7c2]=raw[12]
            c.memory[0x400:0x40e]=raw;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
            for _ in range(80):
                if c.pc==0xa18a:break
                c.step()
            else:raise RuntimeError('Original talk selector did not finish')
            if c.memory[0xa1]==raw[12]:shared.call(c,r.word(10,0xcb08+2*raw[12]))
            witnessed=qty!=0 if mid==41 else bool(qty&128)
            msg=raw[2]if flag&raw[13]or witnessed else raw[1]
            expected=(msg,flag|(raw[13]if witnessed else 0),raw[12]if flag&raw[13]else 0)
            got=c.memory[0x3b],c.memory[fa],c.memory[0x7c2];assert got==expected,(mid,flag,qty,got,expected)
            assert (c.memory[0x580],c.memory[0x5c0])==(14,qty)
            rows.append('\t'.join(map(str,(mid,flag,qty,*got))))
    save('world-west-house-talk-original.tsv',rows)

if __name__=='__main__':main()
