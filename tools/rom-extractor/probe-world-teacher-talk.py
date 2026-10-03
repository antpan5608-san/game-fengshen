"""Targeted original room171 teacher selection and gift capacity evidence.

Controlled CPU execution; derived TSV only. Does not establish item use or join.
"""
import argparse,hashlib,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs
from py65.devices.mpu6502 import MPU

def call(cpu,address):
    cpu.sp=0xff;cpu.stPushWord(0x5fff);cpu.pc=address
    for _ in range(20000):
        if cpu.pc==0x6000:return
        cpu.step()
    raise RuntimeError(f'Original teacher action did not return {cpu.pc:04x}')

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    r=Reader(a.rom.read_bytes());selector=r.read(10,0x8000,32768);grant=r.read(2,0x8000,32768)
    disciple=bytes.fromhex(extract_npcs(r,171)['records'][0]['rawHex'])
    record=bytes.fromhex(extract_npcs(r,171)['records'][1]['rawHex'])
    if(record[0],record[12],record[13])!=(175,12,2):raise ValueError('Original scoped teacher differs')
    fa=r.word(0,0xd493+342)
    disciple_rows=['flagBefore\tpartyCount\tmessage\tflagAfter']
    for flag,party in itertools.product(range(256),(1,2,3,4)):
        c=MPU();c.memory[0x8000:]=selector;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag
        c.memory[0xa2]=1;c.memory[0x500]=party;c.memory[0x400:0x40e]=disciple;c.memory[0x3d]=0;c.memory[0x3e]=4
        c.pc=0xa160
        for _ in range(80):
            if c.pc==0xa18a:break
            c.step()
        else:raise RuntimeError('Original disciple first/repeat selector did not finish')
        if c.memory[0xa1]==11:call(c,0xcc01)
        expected=6 if flag&1 or party==4 else 5;after=flag|1 if party==4 else flag
        if(c.memory[0x3b],c.memory[fa])!=(expected,after):raise AssertionError('Original disciple selector differs')
        disciple_rows.append('\t'.join(map(str,(flag,party,c.memory[0x3b],c.memory[fa]))))
    a.output.parent.mkdir(parents=True,exist_ok=True)
    disciple_raw=('\n'.join(disciple_rows)+'\n').encode('ascii')
    (a.output.parent/'world-room171-disciple-original-cpu.tsv').write_bytes(disciple_raw)
    print('Original disciple cases=1024 sha256='+hashlib.sha256(disciple_raw).hexdigest())
    rows=['flagBefore\twitness\thasItem19\tpartyCount\tglobal7c7\tmessage\tflagAfter'];failures=0
    for flag,witness,has,party,global7 in itertools.product(range(256),(0,1),(0,1),(1,2,3,4),(0,128)):
        cpu=MPU();cpu.memory[0x8000:]=selector;cpu.memory[0xa3]=fa&255;cpu.memory[0xa4]=fa>>8;cpu.memory[fa]=flag
        cpu.memory[0xa2]=2;cpu.memory[0x7c8]=witness;cpu.memory[0x7c7]=global7;cpu.memory[0x500]=party
        cpu.memory[0x580]=19;cpu.memory[0x5c0]=has
        # Execute the actual NPC raw first/repeat selector before action12.
        # A set NPC flag skips the postprocessor; it selects record byte2 (3).
        cpu.memory[0x400:0x40e]=record;cpu.memory[0x3d]=0;cpu.memory[0x3e]=4
        cpu.pc=0xa160
        for _ in range(80):
            if cpu.pc==0xa18a:break
            cpu.step()
        else:raise RuntimeError('Actual original first/repeat selector did not finish')
        if cpu.memory[0xa1]==12:call(cpu,0xcc1b)
        msg=cpu.memory[0x3b];after=cpu.memory[fa]
        expected=record[2] if flag&2 else record[1];newflag=flag
        if not flag&2 and witness:
            expected=1
            if has:
                expected=2 if party<3 else 3 if party<4 else 7
                if party>=4 and global7&128:expected=3;newflag=flag|2
        failures+=(msg,after)!=(expected,newflag)
        rows.append('\t'.join(map(str,(flag,witness,has,party,global7,msg,after))))
    gifts=['case\tinitialRows\tquantityAfter\tgrantError\tmapFlagAfter\tmessageAfter']
    others=[i for i in range(256)if i!=19][:16]
    cases={'empty':[],'already_owned':[(19,1)],'owned_used':[(19,129)],'full_new':[(i,1)for i in others],'full_existing':[(19,1)]+[(i,1)for i in others[:15]],'one_free':[(i,1)for i in others[:15]]}
    for kind,initial in cases.items():
        cpu=MPU();cpu.memory[0x8000:]=grant;cpu.memory[0x675]=171;cpu.memory[0x3b]=1
        for j,(item,q)in enumerate(initial):cpu.memory[0x580+j]=item;cpu.memory[0x5c0+j]=q
        call(cpu,0xb510)
        quantity=next((cpu.memory[0x5c0+j]&127 for j in range(16)if cpu.memory[0x580+j]==19 and cpu.memory[0x5c0+j]&127),0)
        error=cpu.memory[0x68e];got=(quantity,error,cpu.memory[fa],cpu.memory[0x3b])
        expected=(1,1,0,1)if kind in('already_owned','owned_used','full_existing')else(0,1,0,1)if kind=='full_new'else(1,0,0,1)
        failures+=got!=expected
        gifts.append('\t'.join(map(str,(kind,','.join(f'{i}:{q}'for i,q in initial)or'-',*got))))
    if failures:raise AssertionError(f'{failures} original teacher selector/gift differences')
    a.output.mkdir(parents=True,exist_ok=True)
    for name,table in [('selector.tsv',rows),('gift.tsv',gifts)]:
        target=a.output/name;target.write_bytes(('\n'.join(table)+'\n').encode('ascii'))
        print(f'{name}: cases={len(table)-1}, failures=0, sha256={hashlib.sha256(target.read_bytes()).hexdigest()}')
if __name__=='__main__':main()
