"""Scoped target CPU item19 dispatch, reusable bookkeeping, init and completion.
Controlled boundary experiments; not a normal Android route or learned-spell proof.
"""
import argparse,hashlib,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader
from py65.devices.mpu6502 import MPU

def call(c,a):
    c.sp=255;c.stPushWord(0x5fff);c.pc=a
    for _ in range(20000):
        if c.pc==0x6000:return
        c.step()
    raise RuntimeError(f'Original call did not return {c.pc:04x}')
def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();r=Reader(a.rom.read_bytes())
    rows=['kind\tinputA\tinputB\tinputC\toutputA\toutputB']
    for item,mid,target in itertools.product((18,19,20),(109,110,111),(129,130,131)):
        c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x6db]=item;c.memory[0x47]=mid;c.memory[0x6e]=target;c.pc=0xe28c
        for _ in range(30):
            if c.pc in (0xe2a6,0xe55b):break
            c.step()
        else:raise RuntimeError('Original scoped dispatch did not finish')
        selected=c.pc==0xe55b
        assert selected==(item==19 and mid==110 and target==130)
        assert c.memory[0xa5]==(29 if selected else 0)
        rows.append('\t'.join(map(str,('dispatch',item,mid,target,int(selected),c.memory[0xa5]))))
    for index,q in itertools.product((0,8,15),(0,1,129)):
        c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x6d6]=1;c.memory[0x6d7]=19;c.memory[0x580+index]=19;c.memory[0x5c0+index]=q
        call(c,0xa22c);after=c.memory[0x5c0+index]
        assert(after,c.memory[0x580+index])==(q|128 if q else 0,19)
        rows.append('\t'.join(map(str,('bookkeeping',index,q,0,after,c.memory[0x580+index]))))
    fa=r.word(0,0xd493+220)
    for flag in range(256):
        c=MPU();c.memory[0x8000:]=r.read(11,0x8000,32768);c.memory[0xa5]=29;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag;c.memory[0x5b]=17
        call(c,0xcd9a);assert c.memory[fa]==flag|128 and c.memory[0xa5]==0
        # This completion does not write encounter5B. Script termination resets
        # it elsewhere; capture that separately instead of inferring from CD9A.
        assert c.memory[0x5b]==17
        rows.append('\t'.join(map(str,('completion',flag,29,17,c.memory[fa],c.memory[0xa5]))))
    for value in (0,207):
        c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768);pointer=r.word(0,0xd664+220)
        assert pointer==0x7df;c.memory[pointer]=value;c.memory[0xaa]=pointer&255;c.memory[0xab]=pointer>>8;c.pc=0xa777
        for _ in range(10):
            if c.pc==0xa785:break
            c.step()
        else:raise RuntimeError('Overlay selector did not finish')
        assert c.memory[0x05]==value
        rows.append('\t'.join(map(str,('overlay',value,110,0,c.memory[0x05],0))))
    # Actual ordinary enemy targeting uses present-party count; extend only
    # the third actor needed by this join. All-dead stops at the failure branch.
    for alive,byte in itertools.product(range(8),range(256)):
        c=MPU();c.memory[0x8000:]=r.read(9,0x8000,32768);c.memory[0x500]=3;c.memory[0x43]=byte
        for slot in range(3):c.memory[0x12d+slot]=slot+1;c.memory[0x514+2*slot]=int(bool(alive&(1<<slot)))
        c.pc=0x8eb2;c.sp=255;c.stPushWord(0x5fff)
        for _ in range(300):
            if c.pc in (0x6000,0xa60e):break
            c.step()
        else:raise RuntimeError('Original three-party target did not finish')
        initial=(byte>>2)&3
        if initial>=3:initial-=3
        expected=next((slot for slot in list(range(initial,3))+list(range(initial))if alive&(1<<slot)),-1)
        actual=-1 if c.pc==0xa60e else c.memory[0x368]
        assert (actual,c.memory[0x43])==(expected,byte)
        rows.append('\t'.join(map(str,('three-target',alive,byte,0,actual,c.memory[0x43]))))
    table=list(r.read(9,r.word(9,0x8577+4),36))
    for level,byte in itertools.product(list(range(0,60,5))+[79,255],range(256)):
        c=MPU();c.memory[0x8000:]=r.read(9,0x8000,32768);c.memory[0x368]=2;c.memory[0x12f]=3;c.memory[0x506]=level;c.memory[0x43]=byte
        call(c,0x8528);bucket=min(min(level,79)//5,11)
        expected=next((i+1 for i in range(3)if byte<table[bucket+12*i]),4)
        assert (c.memory[0x694d],c.memory[0x43])==(expected,byte)
        rows.append('\t'.join(map(str,('three-multiplier',level,byte,2,c.memory[0x694d],c.memory[0x43]))))
    assert r.read(8,r.word(8,0xb311+414),1)==b'\xff'
    a.output.parent.mkdir(parents=True,exist_ok=True);raw=('\n'.join(rows)+'\n').encode('ascii');a.output.write_bytes(raw)
    multiplier_raw=('\t'.join(map(str,table))+'\n').encode('ascii')
    (a.output.parent/'world-yang-multiplier-original.tsv').write_bytes(multiplier_raw)
    print(f'Original item19 scoped CPU cases={len(rows)-1} failures=0 sha256={hashlib.sha256(raw).hexdigest()}')
if __name__=='__main__':main()
