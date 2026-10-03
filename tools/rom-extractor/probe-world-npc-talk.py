"""Original scoped actor action17. Derived table only; no raw ROM/public saves."""
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
    raise RuntimeError(f'Original CPU action did not return {cpu.pc:04x}')

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    r=Reader(a.rom.read_bytes());bank=r.read(10,0x8000,32768)
    record=bytes.fromhex(extract_npcs(r,110)['records'][0]['rawHex'])
    if (record[0],record[12],record[13])!=(130,17,2):raise ValueError('Original scoped actor differs')
    flag_address=r.word(0,0xd493+220);rows=['flagBefore\thasItem19\twitnessBefore\tmessage\tflagAfter\twitnessAfter'];failures=0
    for flag,has,witness in itertools.product(range(256),(0,1),(0,1)):
        cpu=MPU();cpu.memory[0x8000:]=bank;cpu.memory[0xa3]=flag_address&255;cpu.memory[0xa4]=flag_address>>8
        cpu.memory[flag_address]=flag;cpu.memory[0xa2]=2;cpu.memory[0x7c8]=witness
        cpu.memory[0x3b]=int(flag&2!=0);cpu.memory[0x580]=19;cpu.memory[0x5c0]=has
        call(cpu,0xcc63)
        got=(cpu.memory[0x3b],cpu.memory[flag_address],cpu.memory[0x7c8])
        expected=(int(flag&2!=0 or has),flag|(2 if has and flag&2==0 else 0),witness if flag&2 else 1)
        failures+=got!=expected;rows.append('\t'.join(map(str,(flag,has,witness,*got))))
    if failures:raise AssertionError(f'{failures} original NPC action17 differences')
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_bytes(('\n'.join(rows)+'\n').encode('ascii'))
    print(f'Original action17 cases={len(rows)-1}, failures={failures}, sha256={hashlib.sha256(a.output.read_bytes()).hexdigest()}')
if __name__=='__main__':main()
