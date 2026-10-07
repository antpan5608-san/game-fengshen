"""Room28 original caller, terrain, talk and hidden medicine boundary evidence.

Controlled CPU execution reuses existing village/house probes. Derived TSV only;
not normal play or Android acceptance. Matching ROM is required by Reader.
"""
import argparse
import hashlib
import importlib.util
import itertools
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader, extract_npcs
from py65.devices.mpu6502 import MPU

spec = importlib.util.spec_from_file_location(
    'existing_cpu', Path(__file__).with_name('probe-world-village4.py'))
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)


def run(reader, output):
    records = extract_npcs(reader, 28)['records']
    assert len(records) == 2
    talk, hidden = [bytes.fromhex(n['rawHex']) for n in records]
    assert (talk[0], talk[1], talk[2], talk[12], talk[13]) == (161, 12, 255, 0, 0)
    assert (hidden[0], hidden[1], hidden[2], hidden[12], hidden[13]) == (198, 0, 0, 0, 1)
    assert reader.read(0, 0xd289)[0] == 28
    assert reader.read(8, reader.word(8, 0xdc69 + 28 * 2), 5) == bytes([6, 10, 254, 12, 23])
    output.mkdir(parents=True, exist_ok=True)

    def save(name, rows):
        raw = ('\n'.join(rows) + '\n').encode('ascii')
        (output / name).write_bytes(raw)
        print(name, 'cases', len(rows) - 1, 'failures', 0,
              'sha256', hashlib.sha256(raw).hexdigest())

    rows = ['busy\ttoMap\tcallerAfter']
    for busy in (0, 1):
        c = MPU()
        c.memory[0x8000:] = reader.read(0, 0x8000, 32768)
        c.memory[0x47], c.memory[0x56], c.memory[0x9d] = 0, busy, 99
        shared.call(c, 0xcc49)
        got = c.memory[0x47], c.memory[0x9d]
        assert got == ((0, 99) if busy else (28, 0))
        rows.append('\t'.join(map(str, (busy, *got))))
    save('town-room28-entry-original.tsv', rows)

    rows = ['source\ttarget\tdirection\tblocked']
    for source, target, direction in itertools.product((0, 1, 2, 5), (0, 1, 2, 5), range(1, 5)):
        c = MPU()
        c.memory[0x8000:] = reader.read(0, 0x8000, 32768)
        c.memory[0x71] = 2
        c.memory[0x99], c.memory[0x98], c.memory[0x97] = source, target, direction
        shared.call(c, 0xca98)
        shared.call(c, 0xce35)
        blocked = int(c.memory[0x9c] != 0)
        assert blocked == int(target == 1)
        rows.append('\t'.join(map(str, (source, target, direction, blocked))))
    save('town-room28-terrain-original.tsv', rows)

    flag_address = reader.word(0, 0xd493 + 28 * 2)
    rows = ['flagBefore\tmessage\tflagAfter\taction']
    for flag in range(256):
        c = MPU()
        c.memory[0x8000:] = reader.read(10, 0x8000, 32768)
        c.memory[0x400:0x40e] = talk
        c.memory[0x3d], c.memory[0x3e] = 0, 4
        c.memory[0xa2] = talk[13]
        c.memory[0xa3], c.memory[0xa4] = flag_address & 255, flag_address >> 8
        c.memory[flag_address] = flag
        c.pc = 0xa160
        for _ in range(80):
            if c.pc == 0xa18a:
                break
            c.step()
        else:
            raise RuntimeError('Original room28 talk selector did not finish')
        got = c.memory[0x3b], c.memory[flag_address], c.memory[0xa1]
        assert got == (12, flag, 0)
        rows.append('\t'.join(map(str, (flag, *got))))
    save('town-room28-talk-original.tsv', rows)

    category, item, mask = hidden[1], hidden[2], hidden[13]
    limit = reader.read(2, 0xa190 + category)[0]
    inventory_base = reader.word(2, 0xa194 + 2 * category)
    others = [i for i in range(256) if i != item][:16]
    cases = {'empty': [], 'one': [(item, 1)], 'below_cap': [(item, limit - 1)],
             'at_cap': [(item, limit)], 'used': [(item, 129)],
             'full_new': [(i, 1) for i in others],
             'full_existing': [(item, 1)] + [(i, 1) for i in others[:15]]}
    rows = ['case\tflagBefore\toffered\tapplied\tquantityAfter\tflagAfter']
    for prior, (name, initial) in itertools.product((0, mask, 128, 255), cases.items()):
        c = MPU()
        c.memory[0x8000:] = reader.read(10, 0x8000, 32768)
        c.memory[0xa93a] = 0x60  # Skip presentation only, as in existing house CPU probe.
        c.memory[0x400:0x40e] = hidden
        c.memory[0x3d], c.memory[0x3e], c.memory[0x6e] = 0, 4, 198
        c.memory[0x674], c.memory[0x605], c.memory[0x47] = 1, 3, 28
        c.memory[0xa3], c.memory[0xa4] = flag_address & 255, flag_address >> 8
        c.memory[flag_address] = prior
        for index, (identity, quantity) in enumerate(initial):
            c.memory[inventory_base + index] = identity
            c.memory[inventory_base + 64 + index] = quantity
        before_inventory = bytes(c.memory[inventory_base:inventory_base + 80])
        shared.call(c, 0xa740)
        offered = int(c.memory[0x68f] != 0)
        assert offered == int(not prior & mask)
        assert c.memory[flag_address] == prior
        applied = 0
        if offered:
            assert (c.memory[0x6c0], c.memory[0x6bf]) == (category, item)
            c.memory[0x8000:] = reader.read(2, 0x8000, 32768)
            c.memory[0x6d6], c.memory[0x6d7] = category, item
            shared.call(c, 0xa0eb)
            applied = int(c.memory[0x68e] == 0)
            c.memory[0x682d] = mask
            c.pc = 0x9f2c
            for _ in range(12000):
                if c.pc in (0x9f3a, 0x9f72):
                    break
                c.step()
            else:
                raise RuntimeError('Original success-only pickup flag boundary not reached')
        old = next((quantity & 127 for identity, quantity in initial if identity == item), 0)
        expected = int(bool(offered) and old < limit and (old > 0 or len(initial) < 16))
        quantity = next((c.memory[inventory_base + 64 + i] & 127 for i in range(16)
                         if c.memory[inventory_base + i] == item and c.memory[inventory_base + 64 + i] & 127), 0)
        assert (applied, quantity, c.memory[flag_address]) == (
            expected, old + expected, prior | (mask if expected else 0))
        if not applied:
            assert bytes(c.memory[inventory_base:inventory_base + 80]) == before_inventory
        rows.append('\t'.join(map(str, (name, prior, offered, applied, quantity, c.memory[flag_address]))))
    save('town-room28-hidden-original.tsv', rows)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    run(Reader(args.rom.read_bytes()), args.output)


if __name__ == '__main__':
    main()
