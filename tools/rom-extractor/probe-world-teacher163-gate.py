"""Bounded original island-load NPC-context selector; controlled CPU, not App proof."""
from pathlib import Path
import sys,hashlib,json
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from export_development import iteration_reader
from py65.devices.mpu6502 import MPU

def main():
    r=iteration_reader();bank=r.read(0,0x8000,32768)
    out=Path('private-derived/world-teacher163-gate-cpu');out.mkdir(parents=True,exist_ok=True)
    rows=['map\tpartyCount\tglobal7c6\toverlayBefore\toverlayAfter'];failures=0
    for party in range(5):
        for flag in range(256):
            c=MPU();c.memory[0x8000:]=bank;c.memory[0x47]=79;c.memory[0x500]=party;c.memory[0x7c6]=flag;c.memory[0x7ea]=167;c.pc=0xa664
            for _ in range(30):
                if c.pc in (0xa685,0xa699):break
                c.step()
            else:raise RuntimeError('Original map79 selector did not reach its next branch')
            expected=219 if party>=3 and flag&16==0 else 0
            failures+=c.memory[0x7ea]!=expected
            rows.append('\t'.join(map(str,(79,party,flag,167,c.memory[0x7ea]))))
    for mid in [16,78,163,171]:
        c=MPU();c.memory[0x8000:]=bank;c.memory[0x47]=mid;c.memory[0x500]=3;c.memory[0x7ea]=219;c.pc=0xa664
        for _ in range(20):
            if c.pc==0xa685:break
            c.step()
        else:raise RuntimeError('Unrelated original map load did not finish')
        failures+=c.memory[0x7ea]!=219;rows.append('\t'.join(map(str,(mid,3,0,219,c.memory[0x7ea]))))
    raw=('\n'.join(rows)+'\n').encode('ascii');(out/'selector.tsv').write_bytes(raw)
    result=dict(kind='CONTROLLED_ORIGINAL_CPU_NOT_NORMAL_ANDROID',cases=len(rows)-1,failures=failures,sha256=hashlib.sha256(raw).hexdigest())
    (out/'report.json').write_text(json.dumps(result,indent=2)+'\n');print(result)
    if failures:raise AssertionError('Original NPC context selector differed')
if __name__=='__main__':main()
