"""Scoped action53/54 CPU selector parity; no original ROM modification or Android claim."""
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
    bank=r.read(10,0x8000,32768);rows=['map\tnpc\taction\tmask\tmapFlagBefore\tglobal7c9\tmessage\tmapFlagAfter\teventAfter']
    for map_id,indices in [(8,[0,1]),(9,[0])]:
        records=extract_npcs(r,map_id)['records'];fa=r.word(0,0xd493+2*map_id)
        for index in indices:
            raw=bytes.fromhex(records[index]['rawHex']);action=raw[12];mask=raw[13]
            address=r.word(10,0xcb08+2*action);assert address=={53:0xce4a,54:0xce75}[action]
            table=0xce6e if action==53 else 0xce99
            assert mask in [1,8] and r.read(10,table+mask.bit_length()-1)[0]==raw[2]
            for flag,global9 in itertools.product(range(256),(0,1,2,32,64,128,255)):
                c=MPU();c.memory[0x8000:]=bank;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8
                c.memory[fa]=flag;c.memory[0xa2]=mask;c.memory[0x7c9]=global9;c.memory[0x7c2]=action
                c.memory[0x400:0x40e]=raw;c.memory[0x3d]=0;c.memory[0x3e]=4;c.pc=0xa160
                for _ in range(80):
                    if c.pc==0xa18a:break
                    c.step()
                else:raise RuntimeError('Original selector did not finish')
                if c.memory[0xa1]==action:shared.call(c,address)
                # The generic selector skips the handler on an already-set bit.
                # Preserve its existing scratch event in this controlled caller, as prior probes do.
                expected=(raw[2] if flag&mask or global9 else raw[1],flag|(mask if global9 else 0),action if flag&mask else 0)
                got=c.memory[0x3b],c.memory[fa],c.memory[0x7c2]
                assert got==expected,(map_id,index,flag,global9,got,expected)
                assert c.memory[0x7c9]==global9
                rows.append('\t'.join(map(str,(map_id,index,action,mask,flag,global9,*got))))
    a.output.parent.mkdir(parents=True,exist_ok=True);raw=('\n'.join(rows)+'\n').encode('ascii');a.output.write_bytes(raw)
    print('cases',len(rows)-1,'failures',0,'sha256',hashlib.sha256(raw).hexdigest())
if __name__=='__main__':main()
