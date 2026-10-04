"""Scoped original bridge dispatch and village4 action50, controlled CPU evidence.

Derived TSV only. No raw ROM/save output and no normal App acceptance claim.
"""
import argparse,hashlib,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs
from py65.devices.mpu6502 import MPU

def call(c,address):
    c.sp=255;c.stPushWord(0x5fff);c.pc=address
    for _ in range(12000):
        if c.pc==0x6000:return
        c.step()
    raise RuntimeError(f'Original scoped dispatch did not return: {c.pc:04x}')

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    rows=['source\ttarget\tdirection\tblocked'];bank=r.read(0,0x8000,32768)
    for sc,tc,direction in itertools.product(range(12),range(12),range(1,5)):
        c=MPU();c.memory[0x8000:]=bank;c.memory[0x99]=sc;c.memory[0x98]=tc;c.memory[0x97]=direction
        call(c,0xcb6c);call(c,0xced3)
        rows.append('\t'.join(map(str,(sc,tc,direction,int(c.memory[0x9c]!=0)))))
    raw=('\n'.join(rows)+'\n').encode('ascii');(a.output/'world-town-bridges-original-cpu.tsv').write_bytes(raw)
    print('Bridge cases=576 sha256='+hashlib.sha256(raw).hexdigest())
    rows=['mask\tflagBefore\tglobal7c6\tmessage\tflagAfter\tevent7c2'];bank=r.read(10,0x8000,32768)
    fa=r.word(0,0xd493+8);records=extract_npcs(r,4)['records'];failures=0
    for record in records[1:8]:
        raw=bytes.fromhex(record['rawHex']);mask=raw[13]
        if raw[12]!=50 or mask!=1<<(record['index']-1):raise ValueError('Original action50 actor differs')
        for flag,global6 in itertools.product(range(256),(0,16)):
            c=MPU();c.memory[0x8000:]=bank;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag
            c.memory[0xa2]=mask;c.memory[0x7c6]=global6;c.memory[0x7c2]=50
            c.memory[0x400:0x40e]=raw;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
            for _ in range(80):
                if c.pc==0xa18a:break
                c.step()
            else:raise RuntimeError('Original first/repeat selector did not finish')
            if c.memory[0xa1]==50:call(c,0xcdfa)
            expected=(raw[2],flag,50)if flag&mask else(raw[1]+int(global6!=0),flag|(mask if global6 else 0),0)
            got=(c.memory[0x3b],c.memory[fa],c.memory[0x7c2]);failures+=got!=expected
            # Selector stops before text setup. Group14 is separately captured
            # from the actual original conversation; do not infer it from byte3.
            rows.append('\t'.join(map(str,(mask,flag,global6,*got))))
    if failures:raise AssertionError(f'{failures} action50 differences')
    raw=('\n'.join(rows)+'\n').encode('ascii');(a.output/'world-village4-talk-original-cpu.tsv').write_bytes(raw)
    print('Village action50 cases=3584 failures=0 sha256='+hashlib.sha256(raw).hexdigest())
if __name__=='__main__':main()
