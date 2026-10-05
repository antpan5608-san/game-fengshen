"""Original map136 actor47 and before-text special0 gift; derived CPU rows only."""
import argparse,hashlib,importlib.util,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    record=bytes.fromhex(extract_npcs(r,136)['records'][0]['rawHex']);fa=r.word(0,0xd493+272)
    assert(record[0],record[1],record[2],record[12],record[13])==(182,1,3,47,1)
    assert r.word(10,0xcb08+94)==0xcd75
    def save(name,rows):
        raw=('\n'.join(rows)+'\n').encode('ascii');(a.output/name).write_bytes(raw)
        print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
    rows=['mapFlagBefore\tyangStatus\tquantityByte\tmessage\tmapFlagAfter']
    for flag,status,qty in itertools.product(range(256),(0,32,64,96,128,192,255),(0,1,128,129)):
        c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768);c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8
        c.memory[fa]=flag;c.memory[0xa2]=1;c.memory[0x546]=status;c.memory[0x580]=0;c.memory[0x5c0]=qty
        c.memory[0x400:0x40e]=record;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
        for _ in range(80):
            if c.pc==0xa18a:break
            c.step()
        else:raise RuntimeError('Original selector did not finish')
        if c.memory[0xa1]==47:shared.call(c,0xcd75)
        expected=(3,flag)if flag&1 else(2 if qty&127 else 1,flag)if status&64 else(3,flag|1)
        got=c.memory[0x3b],c.memory[fa];assert got==expected,(flag,status,qty,got,expected)
        assert c.memory[0x546]==status and c.memory[0x5c0]==qty
        rows.append('\t'.join(map(str,(flag,status,qty,*got))))
    save('world-lotus136-selector-original.tsv',rows)
    rows=['message\tcase\tquantityAfter\tgrantFailed\tpaddleIdAfter\tpaddleQtyAfter']
    cases={'empty':[],'existing':[(0,1)],'used_positive':[(0,129)],'used_zero':[(0,128)],
           'empty_used_paddle':[(14,128),(11,129)],'full':[(i,1)for i in range(1,17)]}
    for msg,case in itertools.product((1,2,3),cases):
        c=MPU();c.memory[0x8000:]=r.read(2,0x8000,32768);c.memory[0x675]=136;c.memory[0x3b]=msg
        for i,(ident,q)in enumerate(cases[case]):c.memory[0x580+i]=ident;c.memory[0x5c0+i]=q
        shared.call(c,0xb481)
        qty=next((c.memory[0x5c0+i]for i in range(16)if c.memory[0x580+i]==0 and c.memory[0x5c0+i]),0)
        old=next((q for ident,q in cases[case]if ident==0),0)
        expected=(1 if case in ('empty','used_zero','empty_used_paddle')else old)if msg==1 else old
        assert qty==expected,(msg,case,qty,expected)
        assert c.memory[0x5c1]==129 if case=='empty_used_paddle'else True
        rows.append('\t'.join(map(str,(msg,case,qty,c.memory[0x68e],c.memory[0x580],c.memory[0x5c0]))))
    save('world-lotus136-gift-original.tsv',rows)

if __name__=='__main__':main()
