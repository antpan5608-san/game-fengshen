"""Original room37 action58 and map145 actor filters; controlled CPU only."""
import argparse, hashlib, importlib.util, itertools, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader, extract_npcs
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_call',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    assert r.word(10,0xcb08+2*58)==0xcef8
    def save(name,rows):
        raw=('\n'.join(rows)+'\n').encode('ascii');(a.output/name).write_bytes(raw)
        print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
    rows=['actor\tmask\tflagBefore\tyangStatus\tmessage\tflagAfter\tactionAfter']
    fa=r.word(0,0xd493+2*37)
    for record in extract_npcs(r,37)['records']:
        rec=bytes.fromhex(record['rawHex']);assert rec[12]==58
        for flag,status in itertools.product(range(256),(0,2,32,64,255)):
            c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768)
            c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag
            c.memory[0xa2]=rec[13];c.memory[0x546]=status;c.memory[0x400:0x40e]=rec
            c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
            for _ in range(80):
                if c.pc==0xa18a:break
                c.step()
            else:raise RuntimeError('Original action58 selector did not finish')
            if c.memory[0xa1]==58:shared.call(c,0xcef8)
            repeat=bool(flag&rec[13]);healthy=not bool(status&64)
            expected=(rec[2]if repeat else rec[1]+int(healthy),flag|(rec[13]if healthy and not repeat else 0),0)
            got=(c.memory[0x3b],c.memory[fa],c.memory[0x7c2]);assert got==expected,(flag,status,got,expected)
            rows.append('\t'.join(map(str,(rec[0],rec[13],flag,status,*got))))
    save('jiameng-room37-talk58-original.tsv',rows)
    rows=['actor\tflagBefore\tactorAfter']
    fa=r.word(0,0xd493+2*145)
    for record in extract_npcs(r,145)['records'][1:]:
        rec=bytes.fromhex(record['rawHex']);assert rec[0]in(248,249)
        for flag in range(256):
            c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768)
            c.memory[0x418:0x426]=rec;c.memory[0x47]=145;c.memory[fa]=flag
            c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8
            shared.call(c,0xa973)
            got=c.memory[0x418];assert got==(0 if flag&rec[13] else rec[0]),(rec[0],flag,got)
            rows.append('\t'.join(map(str,(rec[0],flag,got))))
    save('jiameng-map145-barriers-original.tsv',rows)

if __name__=='__main__':main()
