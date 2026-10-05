"""Current Jiameng actor/status completion CPU boundaries; not normal play."""
import argparse, importlib.util, itertools, json, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader,digest
from py65.devices.mpu6502 import MPU
spec=importlib.util.spec_from_file_location('existing_cpu_call',Path(__file__).with_name('probe-world-village4.py'))
shared=importlib.util.module_from_spec(spec);spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());a.output.mkdir(parents=True,exist_ok=True)
    summaries=[]
    def save(name,rows):
        raw=('\n'.join(rows)+'\n').encode();(a.output/name).write_bytes(raw)
        summaries.append(dict(path=name,cases=len(rows)-1,sha256=digest(raw),failures=0))
    bank=r.read(10,0x8000,32768);fa=r.word(0,0xd493+2*145)
    rows=['won\tflagBefore\tflagAfter\tcontextAfter\tactorAfter']
    for won,flag in itertools.product((0,1),(0,2,128,255)):
        c=MPU();c.memory[0x8000:]=bank;c.memory[0x7c1]=won;c.memory[0x7e6]=215
        c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[fa]=flag;c.memory[0xa2]=2;c.memory[0x418]=154
        shared.call(c,0xd283)
        got=(c.memory[fa],c.memory[0x7e6],c.memory[0x418])
        assert got==((flag|2,228,0)if won else(flag,215,154)),got
        rows.append('\t'.join(map(str,(won,flag,*got))))
    save('jiameng-first-victory-original.tsv',rows)
    fa=r.word(0,0xd493+2*146)
    rows=['statusBefore\tmaxHp\tmaxMp\tstatusAfter\thpAfter\tmpAfter\tflagAfter\tcontextAfter\tactorAfter']
    for status,hp,mp in itertools.product((0,2,32,64,255),(1,92,1024,65535),(0,1,54,1024)):
        c=MPU();c.memory[0x8000:]=bank;c.memory[0xa3]=fa&255;c.memory[0xa4]=fa>>8;c.memory[0xa2]=4
        c.memory[0x545]=status;c.memory[0x418]=129
        c.memory[0x51e:0x520]=[hp&255,hp>>8];c.memory[0x52e:0x530]=[mp&255,mp>>8]
        shared.call(c,0xd29a)
        got=(c.memory[0x545],c.memory[0x516]+256*c.memory[0x517],c.memory[0x526]+256*c.memory[0x527],c.memory[fa],c.memory[0x7e7],c.memory[0x418])
        assert got==(0,hp,mp,4,216,0),got
        rows.append('\t'.join(map(str,(status,hp,mp,*got))))
    save('jiameng-xiao-return-original.tsv',rows)
    rows=['yangStatusBefore\tmap145FlagBefore\tyangStatusAfter\tmap145FlagAfter\tmap148Context\tmap101Context\tscript']
    for status,flag in itertools.product((0,2,32,64,255),(0,2,12,128,255)):
        c=MPU();c.memory[0x8000:]=r.read(11,0x8000,32768);c.memory[0xe000:]=r.read(0,0xe000,8192)
        c.memory[0x546]=status;c.memory[0x791]=flag
        shared.call(c,0xcc44)
        got=(c.memory[0x546],c.memory[0x791],c.memory[0x7e8],c.memory[0x7d6],c.memory[0x5c])
        assert got==(status|64,flag|12,217,196,30),got
        rows.append('\t'.join(map(str,(status,flag,*got))))
    save('jiameng-three-victory-original.tsv',rows)
    print(json.dumps(dict(romSha256=digest(r.data),kind='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_ROUTE',results=summaries)))

if __name__=='__main__':main()
