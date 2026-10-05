"""Original boat219 target classes0..24; shore25/26 need separate dispatch.

Derived matrix only, not a completed vehicle, route or Android acceptance.
"""
import argparse,hashlib,importlib.util,itertools,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    rows=['shipBit\tsource\ttarget\tdirection\tblocked\tvehicleAfter\tocclusion']
    for ship,source,target,direction in itertools.product((0,1),range(27),range(25),range(1,5)):
        c=MPU();c.memory[0x8000:]=r.read(0,0x8000,32768)
        for address,value in [(0x71,1),(0x47,16),(0x6815,219),(0x6812,ship),(0x99,source),(0x98,target),(0x97,direction)]:c.memory[address]=value
        shared.call(c,0xca98);shared.call(c,0xce35)
        expected=int(target not in ({4,15,16}|({5,17,14}if ship else set())))
        got=int(c.memory[0x9c]!=0),c.memory[0x6815]
        assert got==(expected,219),(ship,source,target,direction,got,expected)
        rows.append('\t'.join(map(str,(ship,source,target,direction,*got,c.memory[0x9b]))))
    raw=('\n'.join(rows)+'\n').encode('ascii');name='world-west-boat-original.tsv';(a.output/name).write_bytes(raw)
    print(name,'cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())

if __name__=='__main__':main()
