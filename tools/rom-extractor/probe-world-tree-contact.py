"""Scoped original CPU contact dispatch. Requires matched ROM and optional py65.

No emulator/player save is changed. Output is a derived expectation table, not
normal play evidence. Raw ROM and original emulator dumps remain private.
"""
import argparse,hashlib,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,extract_npcs
from py65.devices.mpu6502 import MPU

def call(cpu,address):
    cpu.sp=0xff;cpu.stPushWord(0x5fff);cpu.pc=address
    for _ in range(12000):
        if cpu.pc==0x6000:return
        cpu.step()
    raise RuntimeError(f'Original CPU probe did not return: {cpu.pc:04x}')

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--rom',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    reader=Reader(args.rom.read_bytes());bank=reader.read(0,0x8000,32768)
    records={n['entityByte']:bytes.fromhex(n['rawHex'])for n in extract_npcs(reader,16)['records']}
    cases=set(itertools.product((231,232),range(256),(214,215,216,217),(4,),(2,)))
    cases.update(itertools.product((231,232),(0,1,2,4,6,255),(214,215,216,217),(1,2,3,4),(0,2)))
    rows=['actor\tflag\twalker\tdirection\tadvance\ttransition\tresult']
    failures=0
    for actor,flag,walker,direction,advance in sorted(cases):
        cpu=MPU();cpu.memory[0x8000:]=bank;raw=records[actor]
        cpu.memory[0x418:0x426]=raw;cpu.memory[0x402]=walker
        cpu.memory[0x47]=16;cpu.memory[0x710]=flag
        cpu.memory[0xa3]=0x10;cpu.memory[0xa4]=7
        # Original C68A receives the requested direction in $97; it is not
        # the persistent terrain plane (the idle snapshot has already reset it).
        cpu.memory[0x97]=direction
        tx=int.from_bytes(raw[4:6],'little');ty=int.from_bytes(raw[6:8],'little')
        dx,dy={1:(0,-1),2:(0,1),3:(-1,0),4:(1,0)}[direction]
        px=tx-dx*(16-advance);py=ty-dy*(16-advance)
        cpu.memory[0x406:0x408]=px.to_bytes(2,'little')
        cpu.memory[0x408:0x40a]=py.to_bytes(2,'little')
        call(cpu,0xa973);call(cpu,0xc68a)
        transition=int(cpu.memory[0x47]==107)
        if advance==2:
            expected=int(walker==214 and flag&raw[13]==0)
            failures+=transition!=expected
        rows.append('\t'.join(map(str,(actor,flag,walker,direction,advance,transition,cpu.a))))
        if flag==0 and walker==214:print(actor,direction,advance,transition,cpu.a)
    if failures:raise AssertionError(f'{failures} original contact cases differ')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_bytes(('\n'.join(rows)+'\n').encode('ascii'))
    print(f'Original CPU cases={len(cases)}, failures={failures}, sha256={hashlib.sha256(args.output.read_bytes()).hexdigest()}')

if __name__=='__main__':main()
