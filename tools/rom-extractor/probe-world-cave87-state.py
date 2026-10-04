"""Target event6 return and unavailable-actor projection; controlled CPU only.

Runs unchanged target instructions. Does not load an Android save, claim normal
Boss victory, write ROM bytes, or transport private evidence to an artifact.
"""
import argparse, hashlib, importlib.util, itertools, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader, extract_npcs
from py65.devices.mpu6502 import MPU

spec = importlib.util.spec_from_file_location('existing_cpu_call', Path(__file__).with_name('probe-world-village4.py'))
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--rom', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    reader = Reader(args.rom.read_bytes())
    event_bank = reader.read(11, 0x8000, 32768)
    fixed_bank = reader.read(0, 0xe000, 8192)
    actor_bank = reader.read(1, 0x8000, 32768)
    flag_address = reader.word(0, 0xd493 + 87 * 2)
    assert flag_address == 0x757
    assert reader.read(11, 0xda8d, 3) == bytes([1, 7, 0])
    assert reader.word(11, 0xcb5e + 6 * 2) == 0xce31
    assert reader.read(1, 0x9ea3 + 170)[0] == 156
    records = extract_npcs(reader, 87)['records']
    assert records[0]['entityByte'] == 155
    rows = ['case\twon\tflagBefore\tglobalBefore\tstatusBefore\tflagAfter\tglobalAfter\tstatusAfter\tcontext\tactor\teventAfter']
    failures = 0
    for won, old_flag, global_flag, status in itertools.product([0, 1], [0, 1, 127], [0, 16], range(256)):
        cpu = MPU()
        cpu.memory[0x8000:] = event_bank
        cpu.memory[0xe000:] = fixed_bank
        cpu.memory[0xa3] = flag_address & 255
        cpu.memory[0xa4] = flag_address >> 8
        cpu.memory[flag_address] = old_flag
        cpu.memory[0x7bf] = global_flag
        cpu.memory[0x545] = status
        cpu.memory[0x7c1] = won
        cpu.memory[0xa5] = 6
        cpu.memory[0xa6] = 2
        for i, record in enumerate(records):
            start = 0x418 + i * 22
            cpu.memory[start:start + 14] = bytes.fromhex(record['rawHex'])
        shared.call(cpu, 0xce6a)
        if won:
            assert cpu.memory[flag_address] == old_flag and cpu.memory[0x545] == status
            assert cpu.memory[0xa6] == 3
            shared.call(cpu, 0xce8a)  # $5c=0: original script38 completion boundary.
        actual = (cpu.memory[flag_address], cpu.memory[0x7bf], cpu.memory[0x545],
                  cpu.memory[0x7db], cpu.memory[0x418], cpu.memory[0xa5])
        expected = (old_flag | 128, global_flag | (16 if won else 0), status | (64 if won else 0),
                    201 if won else 0, 0 if won else 155, 0)
        failures += actual != expected
        rows.append('\t'.join(map(str, ('final', won, old_flag, global_flag, status, *actual))))
    # Original C250 first command keeps script38 pending; no departure/flag commit.
    for status in [0, 2, 4, 32, 64, 255]:
        cpu = MPU()
        cpu.memory[0x8000:] = event_bank
        cpu.memory[0xe000:] = fixed_bank
        cpu.memory[0xa3] = flag_address & 255
        cpu.memory[0xa4] = flag_address >> 8
        cpu.memory[0x545] = status
        cpu.memory[0xa5] = 6
        cpu.memory[0xa6] = 3
        cpu.memory[0x5c] = 38
        shared.call(cpu, 0xce8a)
        actual = (cpu.memory[flag_address], cpu.memory[0x7bf], cpu.memory[0x545],
                  cpu.memory[0x7db], 155, cpu.memory[0xa5])
        failures += actual != (0, 0, status, 0, 155, 6)
        rows.append('\t'.join(map(str, ('pending', 1, 0, 0, status, *actual))))
    # 1:8B0E..8B47 projects present identities without deleting actor records.
    exit_bank = reader.read(9, 0x8000, 32768)
    roster_rows = ['count\ttestedActor\tstatus\tactiveCount\tactiveIds\tretainedHp\tsavedHp\trestoredHp\tstatusAfter']
    for count in range(1, 4):
        for tested in range(count):
            for status in range(256):
                cpu = MPU()
                cpu.memory[0x8000:] = actor_bank
                cpu.memory[0x500] = count
                for i in range(count):
                    cpu.memory[0x514 + 2 * i] = 101 + i
                    cpu.memory[0x544 + i] = status if i == tested else 0
                cpu.x = 0
                cpu.y = 0
                cpu.pc = 0x8b0e
                for _ in range(200):
                    if cpu.pc == 0x8b4a:
                        break
                    cpu.step()
                else:
                    raise RuntimeError('Original actor projection did not reach its boundary')
                active = [cpu.memory[0x12d + i] for i in range(cpu.memory[0x500])]
                absent = bool(status & 64)
                expected_ids = [i + 1 for i in range(count) if not (i == tested and absent)]
                hp = cpu.memory[0x514 + tested * 2]
                saved = cpu.memory[0x6899 + tested * 2]
                failures += (active, hp, saved) != (expected_ids, 0 if absent else 101 + tested, 101 + tested if absent else 0)
                failures += cpu.memory[0x544 + tested] != status
                # Restore through the original exit loop before its UI transport.
                cpu.memory[0x8000:] = exit_bank
                cpu.memory[0x6898] = count
                cpu.pc = 0x927c
                for _ in range(200):
                    if cpu.pc == 0x92a8:
                        break
                    cpu.step()
                else:
                    raise RuntimeError('Original actor restoration did not reach its boundary')
                restored = cpu.memory[0x514 + tested * 2]
                exit_status = cpu.memory[0x544 + tested]
                failures += (restored, exit_status, cpu.memory[0x500]) != (101 + tested, status & 247, count)
                roster_rows.append('\t'.join(map(str, (count, tested, status, len(active), ','.join(map(str, active)) or '-', hp, saved, restored, exit_status))))
    money_rows=['moneyBefore\tflagBefore\toffered\tamount\tmoneyAfter\tflagAfter']
    money_record=bytes.fromhex(records[4]['rawHex'])
    assert list(money_record[:3])==[144,4,5] and money_record[13]==8
    assert reader.word(2,reader.word(2,0xe616)+10)==550
    for old,flag in itertools.product([0,1,1234,999449,999450,999950,999999],[0,1,8,128,255]):
        cpu=MPU();cpu.memory[0x8000:]=reader.read(10,0x8000,32768)
        cpu.memory[0xa3]=flag_address&255;cpu.memory[0xa4]=flag_address>>8;cpu.memory[flag_address]=flag
        cpu.memory[0x674]=1;cpu.memory[0x605]=3;cpu.memory[0x6e]=144
        cpu.memory[0x3d]=0x18;cpu.memory[0x3e]=4;cpu.memory[0x418:0x426]=money_record
        shared.call(cpu,0xa740);offered=cpu.memory[0x68f]!=0;amount=0
        cpu.memory[0x501:0x504]=old.to_bytes(3,'little');cpu.memory[0x8000:]=reader.read(2,0x8000,32768)
        if offered:
            cpu.sp=255;cpu.stPushWord(0x5fff);cpu.pc=0x9fb5
            for _ in range(12000):
                if cpu.pc==0x9fef:break
                cpu.step()
            else:raise RuntimeError('Original money amount boundary not reached')
            amount=cpu.memory[0x14]+256*cpu.memory[0x15]
            cpu.memory[0x6c1:0x6c4]=amount.to_bytes(3,'little');shared.call(cpu,0xc530)
        after=int.from_bytes(bytes(cpu.memory[0x501:0x504]),'little')
        expected=(not bool(flag&8),550 if not flag&8 else 0,min(999999,old+550)if not flag&8 else old,flag|8)
        failures+=(offered,amount,after,cpu.memory[flag_address])!=expected
        money_rows.append('\t'.join(map(str,(old,flag,int(offered),amount,after,cpu.memory[flag_address]))))
    if failures:
        raise AssertionError(f'{failures} original event6/projection differences')
    args.output.mkdir(parents=True, exist_ok=True)
    for name, table in [('world-cave87-state-original.tsv', rows), ('world-party-unavailable-original.tsv', roster_rows), ('world-cave87-money-original.tsv',money_rows)]:
        raw = ('\n'.join(table) + '\n').encode('ascii')
        (args.output / name).write_bytes(raw)
        print(name, 'cases', len(table) - 1, 'failures', failures, 'sha256', hashlib.sha256(raw).hexdigest())

if __name__ == '__main__':
    main()
