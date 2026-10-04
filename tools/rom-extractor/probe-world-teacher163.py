"""Bounded original map163 selector and special9 gift; controlled CPU only."""
from pathlib import Path
import sys,itertools,json,hashlib
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from export_development import iteration_reader
from forensics.fengshen246 import extract_npcs
from py65.devices.mpu6502 import MPU

def call(c,address):
    c.sp=255;c.stPushWord(0x5fff);c.pc=address
    for _ in range(20000):
        if c.pc==0x6000:return
        c.step()
    raise RuntimeError(f'Original map163 routine did not return {c.pc:04x}')

def main():
    r=iteration_reader();out=Path('private-derived/world-teacher163-cpu');out.mkdir(parents=True,exist_ok=True)
    record=bytes.fromhex(extract_npcs(r,163)['records'][1]['rawHex'])
    assert (record[0],record[1],record[2],record[12],record[13])==(175,2,3,1,2)
    fa=r.word(0,0xd493+326);bank=r.read(10,0x8000,32768)
    rows=['flagBefore\tmessage\tflagAfter'];failures=0
    for flag in range(256):
        c=MPU();c.memory[0x8000:]=bank;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag
        c.memory[0xa2]=2;c.memory[0x400:0x40e]=record;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
        for _ in range(80):
            if c.pc==0xa18a:break
            c.step()
        else:raise RuntimeError('Original selector did not finish')
        if c.memory[0xa1]==1:call(c,0xcb84)
        got=(c.memory[0x3b],c.memory[fa]);expected=(3,flag)if flag&2 else(2,flag|2)
        failures+=got!=expected;rows.append('\t'.join(map(str,(flag,*got))))
    gifts=['case\tmessage\tflagAfterSelector\tquantity\terror'];grant=r.read(2,0x8000,32768)
    others=[i for i in range(256)if i!=9][:16]
    for name,initial in [('empty',[]),('already',[(9,1)]),('used',[(9,129)]),('full',[(i,1)for i in others]),('one-free',[(i,1)for i in others[:15]])]:
        c=MPU();c.memory[0x8000:]=grant;c.memory[0x675]=163;c.memory[0x3b]=2;c.memory[fa]=2
        for j,(item,q)in enumerate(initial):c.memory[0x580+j]=item;c.memory[0x5c0+j]=q
        call(c,0xb481)
        q=next((c.memory[0x5c0+j]&127 for j in range(16)if c.memory[0x580+j]==9 and c.memory[0x5c0+j]&127),0)
        got=(c.memory[0x3b],c.memory[fa],q,c.memory[0x68e])
        expected=(2,2,0,1)if name=='full'else(2,2,1,1)if name in ('already','used')else(2,2,1,0)
        failures+=got!=expected;gifts.append('\t'.join(map(str,(name,*got))))
    report=dict(kind='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_ANDROID',romSha256=hashlib.sha256(r.data).hexdigest(),selectorCases=256,giftCases=5,failures=failures)
    for name,table in [('selector.tsv',rows),('gift.tsv',gifts)]:
        raw=('\n'.join(table)+'\n').encode();(out/name).write_bytes(raw);report[name]=hashlib.sha256(raw).hexdigest()
    (out/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(report)
    if failures:raise AssertionError('Original selector/gift differs')

if __name__=='__main__':main()
