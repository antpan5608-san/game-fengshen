"""Original enemy60's shared status08 priority at four real target slots.

Controlled CPU only: does not grant resources or prove a normal Android route.
"""
import argparse,importlib.util,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_enemy,digest
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('original_call',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    actual=extract_enemy(r,60);assert actual['remainingBytes'][1]==8
    bank=r.read(9,0x8000,32768);rows=['targetSlot\tstatusBefore\tstatusAfter\thpAfter\totherTargetsUnchanged']
    for target in range(4):
        for status in range(256):
            c=MPU();c.memory[0x8000:]=bank;c.memory[0x367]=3
            c.memory[0x69db+3]=actual['remainingBytes'][1];c.memory[0x368]=0;c.memory[0x12d]=target+1
            for slot in range(4):
                c.memory[0x544+slot]=status if slot==target else 2
                c.memory[0x514+2*slot:0x516+2*slot]=[113,1]
            shared.call(c,0xa0d2)
            hp=c.memory[0x514+2*target]+256*c.memory[0x515+2*target]
            unchanged=all(c.memory[0x544+slot]==2 and c.memory[0x514+2*slot:0x516+2*slot]==[113,1]
                for slot in range(4)if slot!=target)
            after=c.memory[0x544+target]
            assert(after,hp,unchanged)==(8 if status in(0,4,8)else status,369,True)
            rows.append('\t'.join(map(str,(target,status,after,hp,int(unchanged)))))
    raw=('\n'.join(rows)+'\n').encode('ascii');path=a.output/'jiameng-zone29-state08-original.tsv';path.write_bytes(raw)
    print(json.dumps(dict(romSha256=digest(r.data),kind='CONTROLLED_ORIGINAL_CPU_NOT_ANDROID_NORMAL_ROUTE',
        cases=1024,failures=0,path=path.name,sha256=digest(raw))))

if __name__=='__main__':main()
