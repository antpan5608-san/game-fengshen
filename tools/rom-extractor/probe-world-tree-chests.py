"""Original category grant and success-only map flag for three real tree chests.

Uses the existing original CPU probe method; no emulator or real player save is
changed. Repeat/selection behavior is covered separately by existing chest rules.
"""
import argparse,hashlib,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs
from py65.devices.mpu6502 import MPU

def run(cpu,entry,stops):
    cpu.pc=entry
    for _ in range(12000):
        if cpu.pc in stops:return
        cpu.step()
    raise RuntimeError(f'Original chest grant did not return {cpu.pc:04x}')

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--map-id',type=int,action='append',choices=(76,87,99,107,108));a=p.parse_args()
    r=Reader(a.rom.read_bytes());bank=r.read(2,0x8000,32768);rows=['map\tnpc\tcategory\titem\tmax\tcase\tapplied\tquantityAfter\tflagAfter\tmask\tinitialRows'];failures=0
    for mid in (a.map_id or (107,108)):
        for n in extract_npcs(r,mid)['records']:
            raw=bytes.fromhex(n['rawHex'])
            if mid in (76,87) and (raw[0]!=144 or raw[1]==4):continue # Money has its own actual grant path.
            assert raw[0]==144 and raw[12]==0
            cat,item,mask=raw[1],raw[2],raw[13];limit=r.read(2,0xa190+cat)[0];base=r.word(2,0xa194+cat*2)
            others=[i for i in range(256)if i!=item][:16]
            cases={'empty':[],'one':[(item,1)],'below_cap':[(item,limit-1)],'at_cap':[(item,limit)],'used':[(item,129)],'full_new':[(i,1)for i in others],'full_existing':[(item,1)]+[(i,1)for i in others[:15]]}
            for kind,initial in cases.items():
                cpu=MPU();cpu.memory[0x8000:]=bank;cpu.memory[0x6d6]=cat;cpu.memory[0x6d7]=item
                for j,(i,q)in enumerate(initial):cpu.memory[base+j]=i;cpu.memory[base+64+j]=q
                cpu.sp=0xff;cpu.stPushWord(0x5fff);run(cpu,0xa0eb,{0x6000});applied=int(cpu.memory[0x68e]==0)
                fa=r.word(0,0xd493+mid*2);cpu.memory[0xa3]=fa&255;cpu.memory[0xa4]=fa>>8;cpu.memory[0x682d]=mask
                run(cpu,0x9f2c,{0x9f3a,0x9f72});flag=cpu.memory[fa]
                quantities=[cpu.memory[base+64+j]&127 for j in range(16)if cpu.memory[base+j]==item and cpu.memory[base+64+j]&127];quantity=quantities[0]if quantities else 0
                count=next((q&127 for i,q in initial if i==item),0)
                expected=int(count<limit and (count>0 or len(initial)<16))
                failures+=(applied,quantity,flag)!=(expected,count+expected,mask if expected else 0)
                inventory=','.join(f'{i}:{q}'for i,q in initial)or'-'
                rows.append('\t'.join(map(str,(mid,n['index'],cat,item,limit,kind,applied,quantity,flag,mask,inventory))))
    if failures:raise AssertionError(f'{failures} original chest capacity/flag differences')
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(('\n'.join(rows)+'\n').encode('ascii'))
    print(f'Original grant cases={len(rows)-1}, failures={failures}, sha256={hashlib.sha256(a.output.read_bytes()).hexdigest()}')
if __name__=='__main__':main()
