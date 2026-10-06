"""Scoped original Panxi action61 and king action45 selectors/initializer.

Derived numerical TSV only; native scene entry and scripted movement are
separate controlled captures. This does not claim normal Android play.
"""
import argparse,hashlib,importlib.util,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,SHA256
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-yang-join.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());assert hashlib.sha256(r.data).hexdigest()==SHA256
    assert r.word(10,0xcb08+2*45)==0xcd43 and r.word(10,0xcb08+2*61)==0xcf10
    def save(name,rows):
        raw=('\n'.join(rows)+'\n').encode('ascii');a.output.mkdir(parents=True,exist_ok=True)
        (a.output/name).write_bytes(raw)
        print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
    rows=['flagBefore\tcureByte\tpanxiByte\tparty\tmessage\tflagAfter\tactionAfter']
    flag_address=r.word(0,0xd493+2*121)
    for flag,cure,panxi,party in itertools.product((0,16,1,255),(0,1,128,255),(0,1,128,255),(1,2,3,4)):
        c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768)
        c.memory[flag_address]=flag;c.memory[0xa3]=flag_address&255;c.memory[0xa4]=flag_address>>8
        c.memory[0xa2]=16;c.memory[0x7c9]=cure;c.memory[0x7fd]=panxi;c.memory[0x500]=party
        c.memory[0x3b]=17 if flag&16 else 14;c.memory[0xa1]=45;c.memory[0x7c2]=45
        shared.call(c,0xcd43)
        if flag&16 or not cure:expected=(17 if flag&16 else 14,flag,0)
        elif party==4:expected=(17,flag|16,0)
        elif not panxi:expected=(18,flag,0)
        else:expected=(16,flag|16,45)
        got=c.memory[0x3b],c.memory[flag_address],c.memory[0x7c2]
        assert got==expected,(flag,cure,panxi,party,got,expected)
        assert (c.memory[0x7c9],c.memory[0x7fd],c.memory[0x500])==(cure,panxi,party)
        rows.append('\t'.join(map(str,(flag,cure,panxi,party,*got))))
    save('world-jiang-king-selector-original.tsv',rows)
    rows=['panxiBefore\taction\tmessageBefore\tpanxiAfter\tmessageAfter\tactionAfter']
    for panxi,action,msg in itertools.product((0,1,128,255),(0,45,61),(0,6,13)):
        c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768)
        c.memory[0x7fd]=panxi;c.memory[0xa1]=action;c.memory[0x3b]=msg
        shared.call(c,0xcf10)
        assert (c.memory[0x7fd],c.memory[0x3b],c.memory[0x7c2])==(1,13,action)
        rows.append('\t'.join(map(str,(panxi,action,msg,1,13,action))))
    save('world-jiang-panxi-selector-original.tsv',rows)
    c=MPU();c.memory[0x8000:]=r.read(10,0x8000,32768)
    c.memory[0x500:0x600]=[0x55]*256;c.memory[0x6960:0x6970]=[0x55]*16
    c.memory[0x7c3]=0;c.memory[0x500]=3;before=list(c.memory)
    shared.call(c,0xd5c4)
    expected={0xa5:21,0x7d3:192,0x500:4,0x130:4,0x507:37,
        0x511:0x30,0x512:0xe6,0x513:2,0x51a:0x48,0x51b:6,0x522:0x48,0x523:6,
        0x52a:151,0x52b:0,0x532:151,0x533:0,0x696a:235,0x696b:0,
        0x53e:109,0x53f:0,0x537:63,0x543:124,0x56b:24,0x556:100,0x557:0,
        0x563:44,0x54e:160,0x54f:0,0x56f:28,0x38:11,0x39:1,0x7c3:1}
    for addr,value in expected.items():assert c.memory[addr]==value,(hex(addr),c.memory[addr],value)
    # Existing three actors' levels, HP/MP, EXP, stats and all inventory rows stay.
    for start,end in [(0x504,0x507),(0x508,0x511),(0x514,0x51a),(0x51c,0x522),
                      (0x524,0x52a),(0x52c,0x532),(0x534,0x537),(0x538,0x53e),
                      (0x540,0x543),(0x544,0x548),(0x570,0x600)]:
        assert c.memory[start:end]==before[start:end],(hex(start),hex(end))
    save('world-jiang-initializer-original.tsv',['address\tbefore\tafter']+
        [f'{addr:04x}\t{before[addr]}\t{c.memory[addr]}'for addr in sorted(expected)])

if __name__=='__main__':main()
