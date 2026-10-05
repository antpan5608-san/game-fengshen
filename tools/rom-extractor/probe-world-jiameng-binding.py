"""Scoped unchanged-ROM special18 and damage protection CPU checks.

Derived numbers only; controlled inputs do not prove a normal App route.
"""
import argparse, importlib.util, itertools, json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader, digest
from py65.devices.mpu6502 import MPU

spec = importlib.util.spec_from_file_location('existing_cpu_call', Path(__file__).with_name('probe-world-village4.py'))
shared = importlib.util.module_from_spec(spec); spec.loader.exec_module(shared)

def main():
    p=argparse.ArgumentParser();p.add_argument('--rom',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();r=Reader(a.rom.read_bytes());bank=r.read(9,0x8000,32768)
    assert r.word(9,0x890a+18*2)==0x8993
    ids=list(r.read(9,0xaba8,13));markers=list(r.read(9,0xac25,13))
    assert ids[9:]==[158,159,160,161] and markers[9:]==[5]*4
    a.output.mkdir(parents=True,exist_ok=True)
    rows=['enemyId\tmarkerBefore\tmarkerAfter\tquantityAfter\tusedBit']
    for enemy,before in itertools.product(range(256),(0,1,2,3,5,255)):
        c=MPU();c.memory[0x8000:]=bank;c.memory[0x367]=0;c.memory[0x6977]=enemy
        c.memory[0x6948]=before;c.memory[0x5c0]=1
        shared.call(c,0x8993)
        after=c.memory[0x6948];assert after==(5 if enemy in ids[9:] else before)
        assert c.memory[0x5c0]==1
        rows.append(f'{enemy}\t{before}\t{after}\t1\t0')
    effect=('\n'.join(rows)+'\n').encode();(a.output/'jiameng18-binding-original.tsv').write_bytes(effect)
    rows=['enemyId\tmarker\tdamageBefore\tdamageAfter']
    for enemy,marker,damage in itertools.product(ids[9:],(0,1,2,3,5,255),(0,1,64,1000,65535)):
        c=MPU();c.memory[0x8000:]=bank;c.y=ids.index(enemy);c.memory[0x6948]=marker
        c.memory[0x14]=damage&255;c.memory[0x15]=damage>>8;c.pc=0xac32
        for _ in range(20):
            if c.pc in (0xac43,0xadc6):break
            c.step()
        else:raise RuntimeError('Original damage boundary not reached')
        after=c.memory[0x14]+256*c.memory[0x15];assert after==(damage if marker==5 else 0)
        rows.append(f'{enemy}\t{marker}\t{damage}\t{after}')
    gate=('\n'.join(rows)+'\n').encode();(a.output/'jiameng18-damage-original.tsv').write_bytes(gate)
    # Event16's actual RAM writes: don't infer the map from an old label.
    assert [r.word(0,0xd664+2*m) for m in (117,145,121)]==[0x7e2,0x7e6,0x7d0]
    rows=['context145Before\tcontext121Before\tcontext117After\tcontext145After\tcontext121After\tscript']
    for old,other in itertools.product((0,1,128,255),repeat=2):
        c=MPU();c.memory[0x8000:]=r.read(11,0x8000,32768)
        c.memory[0x7e6]=old;c.memory[0x7d0]=other
        shared.call(c,0xd253)
        got=(c.memory[0x7e2],c.memory[0x7e6],c.memory[0x7d0],c.memory[0x5c])
        assert got==(211,215,other,20)
        rows.append('\t'.join(map(str,(old,other,*got))))
    context=('\n'.join(rows)+'\n').encode();(a.output/'jiameng-huang-context-original.tsv').write_bytes(context)
    print(json.dumps(dict(romSha256=digest(r.data),kind='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_APP',
        effectCases=1536,effectSha256=digest(effect),damageCases=120,damageSha256=digest(gate),
        contextCases=16,contextSha256=digest(context),failures=0)))

if __name__=='__main__':main()
