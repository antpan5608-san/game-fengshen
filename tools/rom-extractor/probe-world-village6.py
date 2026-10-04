"""Target map6 action52 and hidden C6 pickup; unchanged CPU, not App evidence."""
import argparse, hashlib, importlib.util, itertools, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader, extract_npcs, SHA256
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
    records=extract_npcs(r,6)['records'];fa=r.word(0,0xd493+12);assert fa==0x706
    assert r.word(10,0xcb70)==0xce32
    bank=r.read(10,0x8000,32768);rows=['npc\tflagBefore\tglobal7c6\tmessage\tflagAfter\teventAfter'];failures=0
    for record in records[:3]:
        raw=bytes.fromhex(record['rawHex']);mask=1<<record['index'];assert raw[12:]==bytes([52,mask])
        for flag,global6 in itertools.product(range(256),[0,1,63,64,65,127,128,255]):
            c=MPU();c.memory[0x8000:]=bank;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag
            c.memory[0xa2]=mask;c.memory[0x7c6]=global6;c.memory[0x7c2]=52
            c.memory[0x400:0x40e]=raw;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
            for _ in range(80):
                if c.pc==0xa18a:break
                c.step()
            else:raise RuntimeError('Original raw first/repeat selector did not finish')
            if c.memory[0xa1]==52:shared.call(c,0xce32)
            witnessed=bool(global6&64)
            expected=(raw[2],flag,52)if flag&mask else(raw[1]+int(witnessed),flag|(mask if witnessed else 0),0)
            got=(c.memory[0x3b],c.memory[fa],c.memory[0x7c2]);failures+=got!=expected
            rows.append('\t'.join(map(str,(record['index'],flag,global6,*got))))
    hidden=bytes.fromhex(records[3]['rawHex']);assert hidden[:3]==bytes([198,0,1])and hidden[12:]==bytes([0,8])
    pickup=['case\tdispatch\tcategory\titem\tapplied\tquantity\tmapFlag']
    for opened in [0,1]:
        c=MPU();c.memory[0x8000:]=bank;c.memory[0xa93a]=0x60 # Animation transport only.
        c.memory[0x400:0x40e]=hidden;c.memory[0x3d]=0;c.memory[0x3e]=4;c.memory[0x6e]=198
        c.memory[0x674]=1;c.memory[0x605]=3;c.memory[0x47]=6;c.memory[0xa3]=6;c.memory[0xa4]=7;c.memory[fa]=opened*8
        c.sp=255;c.stPushWord(0x5fff);c.pc=0xa740
        for _ in range(12000):
            if c.pc==0x6000:break
            c.step()
        else:raise RuntimeError('Original C6 pickup selector did not return')
        got=(c.memory[0x18],c.memory[0x68f],c.memory[fa]);failures+=got!=(47 if not opened else 0,1-opened,opened*8)
        if not opened:failures+=(c.memory[0x6c0],c.memory[0x6bf])!=(0,1)
        pickup.append('\t'.join(map(str,(f'select-{opened}',got[0],0,1,got[1],0,got[2]))))
    bank=r.read(2,0x8000,32768);base=r.word(2,0xa194);others=list(range(2,18))
    for name,initial in {'empty':[],'one':[(1,1)],'below_cap':[(1,9)],'at_cap':[(1,10)],'used':[(1,129)],'full_new':[(i,1)for i in others],'full_existing':[(1,1)]+[(i,1)for i in others[:15]]}.items():
        c=MPU();c.memory[0x8000:]=bank;c.memory[0x6d6]=0;c.memory[0x6d7]=1
        for j,(i,q)in enumerate(initial):c.memory[base+j]=i;c.memory[base+64+j]=q
        shared.call(c,0xa0eb);applied=int(c.memory[0x68e]==0)
        c.memory[0xa3]=6;c.memory[0xa4]=7;c.memory[0x682d]=8;c.pc=0x9f2c
        for _ in range(12000):
            if c.pc in [0x9f3a,0x9f72]:break
            c.step()
        else:raise RuntimeError('Original success-only flag did not finish')
        qty=next((c.memory[base+64+j]&127 for j in range(16)if c.memory[base+j]==1 and c.memory[base+64+j]&127),0)
        prior=next((q&127 for i,q in initial if i==1),0);expected=int(prior<10 and(prior>0 or len(initial)<16))
        failures+=(applied,qty,c.memory[fa])!=(expected,prior+expected,expected*8)
        pickup.append('\t'.join(map(str,(name,47,0,1,applied,qty,c.memory[fa]))))
    if failures:raise AssertionError(f'{failures} original map6 differences')
    a.output.mkdir(parents=True,exist_ok=True)
    for name,table in [('world-village6-talk-original.tsv',rows),('world-village6-hidden-original.tsv',pickup)]:
        f=a.output/name;f.write_bytes(('\n'.join(table)+'\n').encode('ascii'));print(name,'cases',len(table)-1,'failures',failures,'sha256',hashlib.sha256(f.read_bytes()).hexdigest())
if __name__=='__main__':main()
