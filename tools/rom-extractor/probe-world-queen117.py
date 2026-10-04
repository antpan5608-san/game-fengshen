"""Scoped unchanged target CPU: Queen event14 and special13 binding.

Writes derived TSV only. Controlled CPU inputs are not normal-play evidence.
No ROM, original save, private frame or credential is copied to public output.
"""
import argparse, hashlib, importlib.util, itertools, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader, extract_npcs
from py65.devices.mpu6502 import MPU

spec = importlib.util.spec_from_file_location('existing_cpu_call', Path(__file__).with_name('probe-world-village4.py'))
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)

def save(output, name, rows):
    raw = ('\n'.join(rows) + '\n').encode('ascii')
    (output / name).write_bytes(raw)
    print(name, 'cases', len(rows)-1, 'failures', 0, 'sha256', hashlib.sha256(raw).hexdigest(), flush=True)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    reader = Reader(args.rom.read_bytes())
    assert hashlib.sha256(reader.data).hexdigest() == 'f3596ffda5c1b83821e58d15827a3a2fbc94c85352b7a5b834c1039e70509a25'
    args.output.mkdir(parents=True, exist_ok=True)
    assert reader.word(9, 0x890a + 26) == 0x8954
    assert reader.read(11, 0xdaa2, 3) == bytes([7, 5, 0])
    assert reader.word(11, 0xcb5e + 28) == 0xd05c
    assert [reader.word(11, 0xd061 + i*2) for i in range(4)] == [0xd069, 0xd043, 0xd079, 0xd081]
    assert reader.read(11, 0xd069, 2) == bytes([0xa9, 18]) # Actual script18, not19.
    assert reader.read(1, 0x9ea3 + 171, 1) == bytes([157])
    bank = reader.read(9, 0x8000, 32768)
    rows = ['enemyId\tmarkerBefore\tmarkerAfter\tquantityAfter\tusedBit']
    for enemy, marker in itertools.product(range(256), range(6)):
        cpu = MPU(); cpu.memory[0x8000:] = bank
        cpu.memory[0x367] = 3; cpu.memory[0x697a] = enemy
        cpu.memory[0x6948] = marker; cpu.memory[0x5c0] = 1
        shared.call(cpu, 0x8954)
        got = cpu.memory[0x6948]
        assert got == (2 if enemy in (157,174) else marker)
        assert cpu.memory[0x5c0] == 1
        rows.append(f'{enemy}\t{marker}\t{got}\t1\t0')
    save(args.output, 'queen13-binding-original.tsv', rows)
    event_bank = reader.read(11, 0x8000, 32768)
    fixed_bank = reader.read(0, 0xe000, 8192)
    flag_address = reader.word(0, 0xd493 + 117*2)
    records = extract_npcs(reader, 117)['records']
    assert len(records) == 2 and records[1]['entityByte'] == 170
    rows = ['mapFlagBefore\tglobalBefore\tmapFlagAfter\tglobalAfter\tactorAfter\tcontext7e0\tcontext7e1\tcontext7eb\tcontext7e2\teventAfter']
    for flag, global_flag in itertools.product(range(256), (0,16,64,255)):
        cpu = MPU(); cpu.memory[0x8000:] = event_bank; cpu.memory[0xe000:] = fixed_bank
        cpu.memory[0xa3] = flag_address & 255; cpu.memory[0xa4] = flag_address >> 8
        cpu.memory[flag_address] = flag; cpu.memory[0x7c6] = global_flag
        cpu.memory[0xa5] = 14; cpu.memory[0xa6] = 3
        for i, record in enumerate(records):
            start = 0x418 + i*22
            cpu.memory[start:start+14] = bytes.fromhex(record['rawHex'])
        shared.call(cpu, 0xd081)
        got = (cpu.memory[flag_address],cpu.memory[0x7c6],cpu.memory[0x42e],cpu.memory[0x7e0],cpu.memory[0x7e1],cpu.memory[0x7eb],cpu.memory[0x7e2],cpu.memory[0xa5])
        assert got == (flag|128,global_flag|64,0,208,210,220,231,0), (flag,global_flag,got)
        rows.append('\t'.join(map(str,(flag,global_flag,*got))))
    save(args.output, 'queen117-completion-original.tsv', rows)
    # Original castle tileset6 foot dispatch, not a copied town/forest whitelist.
    bank = reader.read(0, 0x8000, 32768)
    rows = ['source\ttarget\tdirection\tblocked\tocclusion\tmode']
    for source, target, direction in itertools.product(range(3),range(3),range(1,5)):
        cpu = MPU(); cpu.memory[0x8000:] = bank
        cpu.memory[0x71]=6; cpu.memory[0x99]=source; cpu.memory[0x98]=target; cpu.memory[0x6c]=direction
        shared.call(cpu,0xca98); shared.call(cpu,0xce35)
        got=cpu.memory[0x9c]!=0
        assert got == (target==1), (source,target,direction,got)
        rows.append('\t'.join(map(str,(source,target,direction,int(got),cpu.memory[0x756],cpu.memory[0x6b]))))
    save(args.output, 'queen-castle6-foot-original.tsv', rows)

if __name__ == '__main__':
    main()
