"""Current three villages' hidden investigation/grant CPU boundaries, not normal play."""
import argparse, hashlib, importlib.util, itertools, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs,SHA256
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
    rows=['map\tnpc\tcategory\titem\tmask\tcase\toffered\tapplied\tvalueAfter\tflagAfter'];count=0
    for mid in (8,9,10):
        fa=r.word(0,0xd493+2*mid)
        for n in extract_npcs(r,mid)['records']:
            raw=bytes.fromhex(n['rawHex'])
            if raw[0]!=198:continue
            cat,item,mask=raw[1],raw[2],raw[13];assert raw[12]==0 and cat in range(5)
            for prior in (0,mask,128,255):
                c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768);c.memory[0xa93a]=0x60
                c.memory[0x400:0x40e]=raw;c.memory[0x3d]=0;c.memory[0x3e]=4;c.memory[0x6e]=198
                c.memory[0x674]=1;c.memory[0x605]=3;c.memory[0x47]=mid;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=prior
                shared.call(c,0xa740);offered=int(c.memory[0x68f]!=0)
                assert (c.memory[0x18],offered,c.memory[fa])==(0 if prior&mask else 47,int(not prior&mask),prior)
                if offered:assert (c.memory[0x6c0],c.memory[0x6bf])==(cat,item)
                rows.append('\t'.join(map(str,(mid,n['index'],cat,item,mask,f'select-{prior}',offered,0,0,prior))))
            if cat==4:
                amount=r.word(2,r.word(2,0xe60e+8)+2*item)
                for old,prior in itertools.product((0,1,1234,999999-amount,999998,999999),(0,mask,128,255)):
                    c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x501:0x504]=old.to_bytes(3,'little')
                    c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=prior
                    offered=int(not prior&mask)
                    if offered:
                        c.memory[0x6c0]=cat;c.memory[0x6bf]=item;c.memory[0x682d]=mask;c.sp=255;c.stPushWord(0x5fff);c.pc=0x9fb5
                        for _ in range(12000):
                            if c.pc==0x9fef:break
                            c.step()
                        else:raise RuntimeError('Original amount boundary not reached')
                        assert c.memory[0x14]+256*c.memory[0x15]==amount
                        c.memory[0x6c1:0x6c4]=amount.to_bytes(3,'little');shared.call(c,0xc530)
                    got=int.from_bytes(bytes(c.memory[0x501:0x504]),'little')
                    assert (got,c.memory[fa])==(min(999999,old+amount) if offered else old,prior|mask)
                    rows.append('\t'.join(map(str,(mid,n['index'],cat,item,mask,f'money-{old}-{prior}',offered,offered,got,c.memory[fa]))))
            else:
                limit=r.read(2,0xa190+cat)[0];base=r.word(2,0xa194+2*cat);others=[i for i in range(256)if i!=item][:16]
                cases={'empty':[],'one':[(item,1)],'below_cap':[(item,limit-1)],'at_cap':[(item,limit)],
                    'used':[(item,129)],'full_new':[(i,1)for i in others],'full_existing':[(item,1)]+[(i,1)for i in others[:15]]}
                for name,initial in cases.items():
                    c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x6d6]=cat;c.memory[0x6d7]=item
                    for i,(ident,quantity)in enumerate(initial):c.memory[base+i]=ident;c.memory[base+64+i]=quantity
                    shared.call(c,0xa0eb);applied=int(c.memory[0x68e]==0)
                    c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[0x682d]=mask;c.pc=0x9f2c
                    for _ in range(12000):
                        if c.pc in (0x9f3a,0x9f72):break
                        c.step()
                    else:raise RuntimeError('Original success-only flag boundary not reached')
                    quantity=next((c.memory[base+64+i]&127 for i in range(16)if c.memory[base+i]==item and c.memory[base+64+i]&127),0)
                    old=next((q&127 for ident,q in initial if ident==item),0);expected=int(old<limit and(old>0 or len(initial)<16))
                    assert (applied,quantity,c.memory[fa])==(expected,old+expected,mask if expected else 0)
                    rows.append('\t'.join(map(str,(mid,n['index'],cat,item,mask,name,1,applied,quantity,c.memory[fa]))))
            count+=1
    assert count==7
    a.output.parent.mkdir(parents=True,exist_ok=True);data=('\n'.join(rows)+'\n').encode('ascii');a.output.write_bytes(data)
    print('records',count,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(data).hexdigest())
if __name__=='__main__':main()
