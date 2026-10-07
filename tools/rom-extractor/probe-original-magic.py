"""Bounded original spell evidence; never an Android or normal-join assertion.

Read the pinned target ROM through the existing Reader and execute its actual
availability prefixes with the existing CPU harness. Healing additionally needs
the independently captured private menu RAM; only its hash is published.
"""
import argparse
import hashlib
import importlib.util
import itertools
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from forensics.fengshen246 import Reader
from py65.devices.mpu6502 import MPU

spec = importlib.util.spec_from_file_location(
    "existing_cpu", Path(__file__).with_name("probe-world-yang-join.py"))
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)

HEAL_RAM_SHA256 = "e011ed846bae4c871d437a426f37b09e920259de41cca0d1519360a9cf15987a"
BATTLE_FIXTURES = {
    "effect": "b22705a526d3a8e0f4ae02087b37d07bcaea7268acc93276dd58137b5d31baab",
    "debit": "541a221a538b14abdc04e09802532e590b6f5d7ecbc3582b2060e1f5fe6a1b24",
    "cure": "2f2c8728768a50196155387abd0370d57f434d01ddbb4c59c86235a9c37f6841",
    "menu": "55e95fe91ffb37d5b69f41bfc117e92c7599b1e97befb2531df155e9e4b6b6e6",
}
BATTLE_CASE_NAMES = (
    "battle-row0-target-status", "battle-row0-heal", "battle-row0-mp-debit",
    "battle-antidote-target-prefix", "battle-post-hp-status", "battle-initial-mp-prefix",
)


def word(cpu, address):
    return cpu.memory[address] + 256 * cpu.memory[address + 1]


def put_word(cpu, address, value):
    cpu.memory[address] = value & 255
    cpu.memory[address + 1] = value >> 8


def write_cases(output, name, rows):
    data = ("\n".join(rows) + "\n").encode("ascii")
    (output / (name + ".tsv")).write_bytes(data)
    return {"name": name, "cases": len(rows) - 1,
            "sha256": hashlib.sha256(data).hexdigest(), "failures": 0}


def directory_records(reader):
    """Preserve unknown rule bytes without enabling or inventing their effects."""
    records = []
    for module, table, identities, counts in (
            (2, 0xeeed, (1, 2, 3), (10, 8, 8)),
            (9, 0xc611, (2, 3, 4), (9, 6, 7))):
        for identity, count in zip(identities, counts):
            pointer = reader.word(module, table + 2 * identity)
            rows = []
            for index in range(count):
                values = list(reader.read(module, pointer + 4 * index, 4))
                assert values[0] < 80 and 0 < values[1] <= 80
                assert values[2] in range(5) and values[3] in range(4)
                rows.append({"rowIndex": index, "levelByte": values[0],
                             "displayedLearnLevel": values[0] + (module == 2),
                             "costByte": values[1], "targetKindByte": values[2],
                             "sceneKindByte": values[3]})
            assert reader.read(module, pointer + 4 * count, 1) == b"\xff"
            records.append({
                "kind": "FIELD_TABLE" if module == 2 else "BATTLE_TABLE",
                "namespace": "fieldSelectorByte" if module == 2 else "battleActiveActorId",
                "identity": identity, "rows": rows,
                "status": "BOUNDED_RECORDS_NOT_ENABLED_SPELL_OPERATIONS",
                "source": reader.span(module, pointer, 4 * count + 1,
                                      "Original records; effect and name bytes not inferred"),
            })
    return records


def availability(reader, output):
    battle = ["activeActorId\tdisplayedLevel\tcount\tfirstTablePointer"]
    observed = {}
    for actor, level in itertools.product((1, 2, 3, 4), range(1, 81)):
        cpu = MPU()
        cpu.memory[0x8000:] = reader.read(9, 0x8000, 32768)
        cpu.memory[0x368] = 0
        cpu.memory[0x12d] = actor
        cpu.memory[0x504 + actor - 1] = level - 1
        shared.call(cpu, 0xb846)
        pointer = word(cpu, 0x12)
        assert pointer == reader.word(9, 0xc611 + 2 * actor)
        count = cpu.memory[0xe6]
        assert 0 <= count <= 9
        observed[actor, level] = count
        battle.append(f"{actor}\t{level}\t{count}\t{pointer}")
    assert all(observed[1, level] == 0 for level in range(1, 81))
    assert [observed[actor, level] for actor, level in
            ((2, 12), (3, 24), (4, 38))] == [2, 1, 4]

    field = ["selectorByte\tdisplayedLevel\tlastIndex\tcount\ttablePointer"]
    field_observed = {}
    for selector, level in itertools.product(range(4), range(1, 81)):
        cpu = MPU()
        cpu.memory[0x8000:] = reader.read(2, 0x8000, 32768)
        cpu.memory[0x6d6] = selector
        cpu.memory[0x504 + selector] = level - 1
        cpu.pc = 0x9466
        for _ in range(2000):
            if cpu.pc == 0x94a1:
                break
            cpu.step()
        else:
            raise RuntimeError("Original field prefix did not finish")
        last = cpu.memory[0x6d7]
        count = (last + 1) & 255
        pointer = word(cpu, 5)
        assert pointer == reader.word(2, 0xeeed + 2 * selector)
        assert 1 <= count <= 10
        field_observed[selector, level] = count
        field.append(f"{selector}\t{level}\t{last}\t{count}\t{pointer}")
    assert [field_observed[selector, level] for selector, level in
            ((1, 12), (2, 24), (3, 38))] == [2, 2, 5]
    return [write_cases(output, "battle-availability-controlled-original", battle),
            write_cases(output, "field-prefix-availability-controlled-original", field)]


def healing(reader, output, fixture):
    raw = fixture.read_bytes()
    if len(raw) != 0x800 or hashlib.sha256(raw).hexdigest() != HEAL_RAM_SHA256:
        raise ValueError("Exact independently captured private menu RAM required")
    rows = ["spellIndex\tparty0\tparty1\tnezhaStoredLevel\txiaoStoredLevel\t"
            "targetIndex\tbeforeHP\tmaximumHP\tafterHP\tafterMP\tformulaLevelOffset"]
    for spell, party, nlv, xlv, target, before, maximum in itertools.product(
            (0, 4), ((0, 1), (1, 0)), (0, 24), (0, 11, 39),
            (0, 1), (0, 5, 190), (20, 200)):
        if before > maximum:
            continue
        cpu = MPU()
        cpu.memory[:0x800] = raw
        cpu.memory[0x8000:] = reader.read(2, 0x8000, 32768)
        cpu.memory[0x126:0x128] = party
        cpu.memory[0x504] = nlv
        cpu.memory[0x505] = xlv
        cpu.memory[0x68b] = party.index(1)
        cpu.memory[0x605] = party.index(target)
        cpu.memory[0x6db] = spell
        cost = 3 if spell == 0 else 12
        cpu.memory[0x6d8] = cost
        put_word(cpu, 0x526, 100)
        put_word(cpu, 0x514 + 2 * target, before)
        put_word(cpu, 0x51c + 2 * target, maximum)
        cpu.pc = 0x918b
        skipped = []
        for _ in range(5000):
            if cpu.pc == 0x924e:
                break
            if cpu.memory[cpu.pc:cpu.pc + 3] == [0x20, 0x4f, 0x80]:
                skipped.append(cpu.pc)
                cpu.pc += 3  # Message renderer only; no effect instruction skipped.
            else:
                cpu.step()
        else:
            raise RuntimeError("Original heal prefix did not finish")
        assert skipped == [0x91d7]
        offset = 0 if party[0] == 1 else 1
        level = (nlv, xlv)[offset]
        amount = 3 * level + 20 if spell == 0 else 10 * level + 300
        hp, mp = word(cpu, 0x514 + 2 * target), word(cpu, 0x526)
        assert hp == min(maximum, before + amount) and mp == 100 - cost
        rows.append("\t".join(map(str, (
            spell, *party, nlv, xlv, target, before, maximum, hp, mp,
            hex(0x504 + offset)))))
    return write_cases(output, "heal-controlled-original", rows)


def field_validation(reader, output, fixture):
    """Execute native-observed menu predicates, before any MP/effect commit."""
    raw = fixture.read_bytes()
    if len(raw) != 0x800 or hashlib.sha256(raw).hexdigest() != HEAL_RAM_SHA256:
        raise ValueError("Exact independently captured private menu RAM required")
    results = []
    for kind, entry, phase, expected_message in (
            ("caster", 0xa2a4, 7, 0xa2c6),
            ("heal-target", 0xa393, 12, 0xa3d8)):
        rows = ["spellIndex\tstatusByte\tphaseBefore\tphaseAfter\trefused\tMPBefore\tMPAfter"]
        for spell, status in itertools.product((0,) if kind == "caster" else (0, 4), range(256)):
            cpu = MPU()
            cpu.memory[:0x800] = raw
            cpu.memory[0x8000:] = reader.read(2, 0x8000, 32768)
            cpu.memory[0x6e4] = 0
            cpu.memory[0x605] = 1 if kind == "caster" else 0
            cpu.memory[0x68b] = 1
            cpu.memory[0x6db] = spell
            cpu.memory[0x544 + (kind == "caster")] = status
            cpu.memory[0x60a] = phase
            before_mp = word(cpu, 0x526)
            cpu.sp = 255
            cpu.stPushWord(0x5fff)
            cpu.pc = entry
            skipped = []
            for _ in range(500):
                if cpu.pc == 0x6000:
                    break
                if cpu.memory[cpu.pc:cpu.pc + 3] == [0x20, 0x4f, 0x80]:
                    skipped.append(cpu.pc)
                    cpu.pc += 3  # Native refusal message renderer only.
                else:
                    cpu.step()
            else:
                raise RuntimeError("Original field validation did not return")
            refused = status & 0xf0 != 0
            assert skipped == ([expected_message] if refused else [])
            assert cpu.memory[0x60a] == phase + (not refused)
            assert cpu.memory[0x6e4] == int(refused)
            assert word(cpu, 0x526) == before_mp
            assert cpu.memory[0x514:0x524] == list(raw[0x514:0x524])
            rows.append("\t".join(map(str, (spell, status, phase, cpu.memory[0x60a],
                                         int(refused), before_mp, word(cpu, 0x526)))))
        results.append(write_cases(output, "field-" + kind + "-status-controlled-original", rows))

    rows = ["cost\tMPBefore\taccepted\tMPAfter"]
    for cost, mp in itertools.product((3, 12), (*range(1024), 4095, 65535)):
        cpu = MPU()
        cpu.memory[:0x800] = raw
        cpu.memory[0x8000:] = reader.read(2, 0x8000, 32768)
        cpu.memory[0x68b] = 1
        cpu.memory[0x6d8] = cost
        cpu.memory[0x6d9] = 0
        put_word(cpu, 0x526, mp)
        cpu.pc = 0x90da
        for _ in range(100):
            if cpu.pc in (0x9110, 0x9104):
                break
            cpu.step()
        else:
            raise RuntimeError("Original field MP prefix did not finish")
        accepted = cpu.pc == 0x9110
        assert accepted == (mp >= cost) and word(cpu, 0x526) == mp
        assert cpu.memory[0x514:0x524] == list(raw[0x514:0x524])
        rows.append(f"{cost}\t{mp}\t{int(accepted)}\t{word(cpu, 0x526)}")
    results.append(write_cases(output, "field-mp-validation-controlled-original", rows))
    return results


def battle_foundation(reader, output, fixtures):
    """Native-observed bounded entries; no rendering instruction or RNG is skipped.

    Mutated masks/high MP are controlled CPU inputs, not admitted player saves.
    Effects, post-HP status, debit and initial selection are separate phases.
    """
    def cpu(kind):
        c = MPU()
        c.memory[:0x800] = fixtures[kind]
        c.memory[0x8000:] = reader.read(9, 0x8000, 32768)
        return c

    def prefix(c, entry, stops):
        c.pc = entry
        for _ in range(5000):
            if c.pc in stops:
                return
            c.step()
        raise RuntimeError("Original battle prefix did not finish")

    results = []
    rows = ["targetStatus\tentry\tstop\tafterHP\tafterMP\tafterStatus\tinstructions"]
    for status in range(256):
        c = cpu("effect"); c.memory[0x544] = status; c.pc = 0x9e84
        for steps in range(5000):
            if c.pc in (0x9e8e, 0x9fda):
                break
            c.step()
        else:
            raise RuntimeError("Original healing target prefix did not finish")
        hp, mp = word(c, 0x514), word(c, 0x526)
        assert hp == (5 if status & 32 else 58) and mp == 44 and c.memory[0x544] == status
        rows.append(f"{status}\t9E84\t{c.pc:04X}\t{hp}\t{mp}\t{status}\t{steps}")
    results.append(write_cases(output, BATTLE_CASE_NAMES[0], rows))

    rows = ["partyIds\tcasterSlot\ttargetSlot\txiaoStoredLevel\tbeforeHP\tmaxHP\tafterHP\tafterMP\tamountAtEffect"]
    for party, level, target, before, maximum in itertools.product(
            ((1, 2), (2, 1), (1, 2, 3, 4), (4, 3, 2, 1)), range(80), (0, 1), (0, 5, 190), (20, 200)):
        if before > maximum:
            continue
        c = cpu("effect"); c.memory[0x12d:0x12d + len(party)] = party
        c.memory[0x368] = party.index(2); c.memory[0xef] = target; c.memory[0x505] = level
        actor = party[target] - 1
        put_word(c, 0x514 + 2 * actor, before); put_word(c, 0x51c + 2 * actor, maximum)
        c.memory[0x544 + actor] = 0; c.pc = 0x9e84; amount = None
        for _ in range(5000):
            if c.pc == 0xa026:
                amount = word(c, 0x14)
            if c.pc == 0x9fda:
                break
            c.step()
        else:
            raise RuntimeError("Original battle heal did not finish")
        hp, mp = word(c, 0x514 + 2 * actor), word(c, 0x526)
        assert amount == 3 * level + 20 and hp == min(maximum, before + amount) and mp == 44
        rows.append("\t".join(map(str, (",".join(map(str, party)), party.index(2), target,
                                         level, before, maximum, hp, mp, amount))))
    results.append(write_cases(output, BATTLE_CASE_NAMES[1], rows))

    rows = ["beforeMP\tafterMP\tNHP\tXHP\tcost"]
    for mp in list(range(1024)) + [4095, 65535]:
        c = cpu("debit"); put_word(c, 0x526, mp); shared.call(c, 0xa8ae)
        after = word(c, 0x526)
        assert after == max(0, mp - 3) and word(c, 0x514) == 58 and word(c, 0x516) == 92
        rows.append(f"{mp}\t{after}\t58\t92\t3")
    results.append(write_cases(output, BATTLE_CASE_NAMES[2], rows))

    rows = ["targetStatus\tstop\taccepted\tHP\tMP\tunchangedStatus"]
    for status in range(256):
        c = cpu("cure"); c.memory[0x544] = status
        prefix(c, 0x9435, (0x943f, 0x9452))
        accepted = c.pc == 0x943f
        assert accepted == (status == 2) and c.memory[0x544] == status
        assert word(c, 0x514) == 5 and word(c, 0x526) == 44
        rows.append(f"{status}\t{c.pc:04X}\t{int(accepted)}\t5\t44\t{status}")
    results.append(write_cases(output, BATTLE_CASE_NAMES[3], rows))

    rows = ["actorSlot\tHP\tmaxHP\tbeforeStatus\tafterStatus"]
    for actor, hp, status in itertools.product(range(4), (0, 5, 49, 50, 200), range(256)):
        c = cpu("effect"); c.memory[0x12d:0x131] = [1, 2, 3, 4]
        c.memory[0x368] = actor; c.memory[0x544 + actor] = status
        put_word(c, 0x514 + 2 * actor, hp); put_word(c, 0x51c + 2 * actor, 200)
        shared.call(c, 0xa752)
        expected = (32 if hp == 0 else status if status >= 16 else
                    (1 if status == 0 else status) if hp < 50 else 0 if status == 1 else status)
        assert c.memory[0x544 + actor] == expected and word(c, 0x514 + 2 * actor) == hp
        assert word(c, 0x526) == 44
        rows.append(f"{actor}\t{hp}\t200\t{status}\t{expected}")
    results.append(write_cases(output, BATTLE_CASE_NAMES[4], rows))

    rows = ["beforeMP\tcost\taccepted\tstop\tafterMP\tHP"]
    for mp in list(range(1024)) + [4095, 65535]:
        c = cpu("menu"); c.a, c.x, c.y = 3, 1, 1; put_word(c, 0x526, mp)
        prefix(c, 0xbd7a, (0xbd91, 0xbe05))
        accepted = c.pc == 0xbd91
        assert accepted == (mp >= 3) and word(c, 0x526) == mp and word(c, 0x514) == 5
        rows.append(f"{mp}\t3\t{int(accepted)}\t{c.pc:04X}\t{mp}\t5")
    results.append(write_cases(output, BATTLE_CASE_NAMES[5], rows))
    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rom", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--heal-ram", type=Path)
    for kind in BATTLE_FIXTURES:
        parser.add_argument("--battle-" + kind + "-ram", type=Path)
    args = parser.parse_args()
    reader = Reader(args.rom.read_bytes())
    names = ("original-magic-report.json", "battle-availability-controlled-original.tsv",
             "field-prefix-availability-controlled-original.tsv", "heal-controlled-original.tsv",
             "field-caster-status-controlled-original.tsv",
             "field-heal-target-status-controlled-original.tsv",
             "field-mp-validation-controlled-original.tsv")
    names += tuple(name + ".tsv" for name in BATTLE_CASE_NAMES)
    if any((args.output / name).exists() for name in names):
        raise ValueError("Fresh output directory required; preserve earlier evidence")
    if args.heal_ram is not None:
        raw = args.heal_ram.read_bytes()
        if len(raw) != 0x800 or hashlib.sha256(raw).hexdigest() != HEAL_RAM_SHA256:
            raise ValueError("Exact independently captured private menu RAM required")
    fixtures = {}
    for kind, expected in BATTLE_FIXTURES.items():
        path = getattr(args, "battle_" + kind + "_ram")
        if path is not None:
            data = path.read_bytes()
            if len(data) != 0x800 or hashlib.sha256(data).hexdigest() != expected:
                raise ValueError("Exact independently captured private battle " + kind + " RAM required")
            fixtures[kind] = data
    if fixtures and len(fixtures) != len(BATTLE_FIXTURES):
        raise ValueError("All four original battle fixtures required before producing any evidence")
    args.output.mkdir(parents=True, exist_ok=True)
    results = availability(reader, args.output)
    if args.heal_ram is not None:
        results.append(healing(reader, args.output, args.heal_ram))
        results.extend(field_validation(reader, args.output, args.heal_ram))
    if fixtures:
        results.extend(battle_foundation(reader, args.output, fixtures))
    report = {
        "status": "SCOPED_ORIGINAL_CPU_EVIDENCE_NOT_NORMAL_JOIN_OR_ANDROID",
        "romSha256": hashlib.sha256(reader.data).hexdigest(),
        "scriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "results": results,
        "directoryRecords": directory_records(reader),
        "entrySources": [
            reader.span(9, 0xb846, 67, "Battle ActiveActorId and displayed-level count"),
            reader.span(2, 0x9466, 59, "Field prefix; stopped before rendering"),
        ],
        "limits": [
            "Field selector0 aliases Xiao table; it is not permission for Nezha magic",
            "Reference role IDs, original actorIndex and battle ActiveActorId differ",
            "No spell name, full target legality, normal growth/join or Android assertion",
            "Healing starts after menu validation; supplied target/cost are controlled",
            "Only message JSR804F skipped; stop924E before UI; no unknown target rule assumed",
            "Native initial uncapped heal observed53 independently; other cases remain CPU-controlled",
            "Original party search increments once without looping; do not substitute reference formula",
            "Field status predicates scoped to Xiao caster and healing rows0/4; only refusal renderer skipped",
            "MP prefix stops before scene dispatch; acceptance alone does not authorize a spell or apply an effect",
        ],
    }
    if args.heal_ram is not None:
        report["controlledNativeFixtureSha256"] = HEAL_RAM_SHA256
        report["entrySources"] += [
            reader.span(2, 0x918b, 195, "Native-observed MP deduction and heal effect prefix"),
            reader.span(2, 0xeebd, 48, "Original integer multiply/add/divide"),
            reader.span(2, 0xa2a4, 52, "Native-observed caster status validation; message call excluded"),
            reader.span(2, 0xa393, 82, "Native-observed healing target status validation; message call excluded"),
            reader.span(2, 0x90da, 54, "MP validation before scene dispatch or deduction"),
        ]
    if fixtures:
        report["battleFixtureSha256"] = BATTLE_FIXTURES
        report["entrySources"] += [
            reader.span(9, 0x9e84, 22, "Row0 target death-bit predicate and effect dispatch"),
            reader.span(9, 0xa026, 86, "Battle heal HP cap; MP is not debited here"),
            reader.span(9, 0xaf8a, 90, "Original fixed-actor level multiply and formula constants"),
            reader.span(9, 0xa8ae, 58, "Native-observed late MP debit and underflow clamp"),
            reader.span(9, 0x9435, 30, "Exact-state2 cure predicate; prefix stops before renderer/effect"),
            reader.span(9, 0xa752, 109, "Native-observed post-HP state routine"),
            reader.span(9, 0xbd7a, 23, "Initial MP compare before target selection"),
            reader.span(9, 0xb196, 60, "Original fixed identity lookup, distinct from field one-step search"),
        ]
        report["limits"] += [
            "Battle prefixes, native whole-action traces and Android acceptance are separate",
            "No renderer or effect instruction skipped in battle cases; cure prefix stops before renderer",
            "Target masks and high MP are controlled, not necessarily legal player saves or maxMP growth",
            "Battle target death bit20 differs from field targetF0; initial dead/state64 selection verified separately",
            "State1 native Chinese label remains unverified; do not apply battle post-HP cleanup to field magic",
            "Row0 healing and row1 predicate/debit only; no general attack/control/RNG/Boss rule inferred",
        ]
    (args.output / "original-magic-report.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
