"""Synthetic transport fixtures exercise release rejection only; never App evidence."""
import copy
import hashlib
import json
import tempfile
import unittest
import subprocess
import os
from unittest.mock import patch
from pathlib import Path
from PIL import Image
from tools import battle_magic_evidence as magic


class BattleMagicEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        actors = [dict(id=name, level=12, experience=2000, hp=92, maxHp=92, mp=44,
            maxMp=44, strength=22, stamina=14, agility=26, spirit=43,
            equipment=dict(rightHand=19, leftHand=-1, body=11, feet=38))
            for name in ('nezha', 'xiaolongnv', 'yangjian', 'jiangziya')]
        actors[0].update(hp=5, maxHp=200, statusMask=2)
        self.before = dict(saveSchemaVersion=1, contentVersion='opening-segment-001-c62',
            mapId=0, x=40, y=248, direction='UP', characters=actors, terrainMode=0,
            inventory={}, flags={}, money=10, encounterSteps=0)
        self.after = copy.deepcopy(self.before)
        self.after['characters'][0]['hp'] = 58
        self.after['characters'][0].pop('statusMask')
        self.after['characters'][1]['mp'] = 38
        self.write('touch-ux-battle-magic-expected-save.json', self.after)
        self.write('world-battle-magic-cold-boundary.json', dict(kind='ACTUAL_APP_EXTERNAL_COLD_BOUNDARY',
            before=self.after, after=self.after, differentTopLevelFields=[], equal=True))
        phases = []
        for stage, mp, hp, status, kind in magic.STAGES:
            hps = {c['id']: c['hp'] for c in actors}
            mps = {c['id']: c['mp'] for c in actors}
            statuses = {c['id']: c.get('statusMask', 0) for c in actors}
            hps['nezha'] = hp
            mps['xiaolongnv'] = mp
            statuses['nezha'] = status
            phases.append(dict(stage=stage, casterMP=mp, targetHP=hp, targetStatus=status,
                actorID='xiaolongnv', targetID='nezha', actionKind=kind,
                partyHP=hps, partyMP=mps, partyStatus=statuses))
        for font in ('1_0', '1_3', '2_0'):
            self.write('touch-ux-battle-magic-font-' + font + '.json',
                dict(kind=magic.KIND, font=float(font.replace('_', '.')),
                    width=160, height=90, screenWidth=160, screenHeight=100,
                    selectionCancelMultiPointerUnchanged=True, staleAndRepeatUnchanged=True,
                    partyLabelsAndMpHeaderFit=True, fullSavePersisted=True,
                    before=self.before, afterMagic=self.after, after=self.after, phases=phases))
            for stage in ('selection', 'antidote-selection', 'saved', *(x[0] for x in magic.STAGES)):
                Image.new('RGB', (160, 100), (1, 2, 3)).save(
                    self.folder / ('touch-ux-battle-magic-' + stage + '-font-' + font + '.png'))
        Image.new('RGB', (160, 100), (1, 2, 3)).save(self.folder / 'touch-ux-battle-magic-cold-full-state.png')
        segments = []
        for name in ('world-battle-magic-normal-00.mp4', 'world-battle-magic-cold-restart.mp4'):
            raw = b'SYNTHETIC_TRANSPORT_TEST_BYTES_NOT_APP_VIDEO'
            (self.folder / name).write_bytes(raw)
            segments.append(dict(file='artifacts/checkpoint-ui/' + name,
                sha256=hashlib.sha256(raw).hexdigest(), savedWorldBefore=self.after, savedWorldAfter=self.after))
        self.write('world-battle-magic-recording.json', dict(
            source='Actual Android App screenrecord; SILENT, no sound validation',
            kind='CONTROLLED_BATTLE_MAGIC_SMOKE', controlledAssertions='PASS', normalAssertions='NOT_APPLICABLE',
            forceStopRestartEqual=True, continuedExploration=False, originalPreferencesRestored=True, segments=segments))

    def write(self, name, value):
        (self.folder / name).write_text(json.dumps(value), encoding='utf-8')

    def mutate(self, name, mutation):
        value = json.loads((self.folder / name).read_text(encoding='utf-8'))
        mutation(value)
        self.write(name, value)

    def test_synthetic_transport_and_changed_raw_video(self):
        result = magic.proof_digests(self.folder)
        magic.validate_digests(result)
        (self.folder / 'world-battle-magic-normal-00.mp4').write_bytes(b'CHANGED')
        with self.assertRaisesRegex(ValueError, 'video bytes changed'):
            magic.proof_digests(self.folder)

    def test_phase_cannot_show_final_mp_early(self):
        self.mutate('touch-ux-battle-magic-font-1_3.json', lambda v: v['phases'][0].update(casterMP=38))
        with self.assertRaisesRegex(ValueError, 'phase differs'):
            magic.proof_digests(self.folder)

    def test_phase_cannot_hide_another_party_member_change(self):
        self.mutate('touch-ux-battle-magic-font-2_0.json', lambda v: v['phases'][1]['partyMP'].update(yangjian=0))
        with self.assertRaisesRegex(ValueError, 'complete party phase differs'):
            magic.proof_digests(self.folder)

    def test_cold_equality_flag_does_not_override_full_save(self):
        self.mutate('world-battle-magic-cold-boundary.json', lambda v: v['after'].update(money=99))
        with self.assertRaisesRegex(ValueError, 'cold save differs'):
            magic.proof_digests(self.folder)

    def test_pre_victory_change_cannot_hide_unrelated_inventory(self):
        self.mutate('touch-ux-battle-magic-font-1_3.json', lambda v: v['afterMagic']['inventory'].update(herb=1))
        with self.assertRaisesRegex(ValueError, 'unrelated pre-victory'):
            magic.proof_digests(self.folder)

    def test_missing_font_image_and_incomplete_actor_are_rejected(self):
        path = self.folder / 'touch-ux-battle-magic-cure-debit-font-2_0.png'
        path.unlink()
        with self.assertRaisesRegex(ValueError, 'screenshot'):
            magic.proof_digests(self.folder)
        Image.new('RGB', (160, 100), (1, 2, 3)).save(path)
        self.mutate('touch-ux-battle-magic-font-2_0.json', lambda v: v['before']['characters'][1].pop('equipment'))
        with self.assertRaisesRegex(ValueError, 'complete four-role'):
            magic.proof_digests(self.folder)

    def test_jvm_oracles_are_exact_committed_native_tables(self):
        root = Path(__file__).resolve().parents[1]
        resources = root / 'android/app/src/test/resources/original-battle-magic'
        sources = root / 'game-data/provenance/expected/original-battle-magic'
        for path in sources.glob('*.tsv'):
            self.assertEqual(path.read_bytes(), (resources / path.name).read_bytes(), path.name)
        self.assertEqual(6, len(list(sources.glob('*.tsv'))))
        learning = root / 'game-data/provenance/expected/original-magic/battle-availability-controlled-original.tsv'
        self.assertEqual(learning.read_bytes(), (resources / learning.name).read_bytes())

    def test_original_evidence_collector_keeps_new_names_and_excludes_raw_inputs(self):
        # Execute the real existing PYEVIDENCE block with a synthetic adb transport;
        # no emulator/App claim and no shell or device access in this test.
        root = Path(__file__).resolve().parents[1]
        source = (root / 'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
        code = source.split("python - <<'PYEVIDENCE'\n", 1)[1].split('\nPYEVIDENCE\n', 1)[0]
        listing = 'battle-magic-font-1_0.json\nbattle-magic-heal-effect-font-2_0.png\nworld-field-magic-font-1_0.json\nprivate-ram.bin\ntarget.nes\nplayer-save.json\n'
        pulls = []
        def transport(args, **kwargs):
            if args[1:3] == ['shell', 'ls']:
                return subprocess.CompletedProcess(args, 0, listing, '')
            self.assertEqual(['adb', 'pull'], args[:2])
            pulls.append(Path(args[3]).name)
            return subprocess.CompletedProcess(args, 0, '', '')
        original = Path.cwd()
        try:
            os.chdir(self.folder)
            with patch('subprocess.run', side_effect=transport):
                exec(compile(code, 'actual-ci-PYEVIDENCE', 'exec'), {})
        finally:
            os.chdir(original)
        self.assertEqual(['touch-ux-battle-magic-font-1_0.json',
                          'touch-ux-battle-magic-heal-effect-font-2_0.png',
                          'touch-ux-world-field-magic-font-1_0.json'], pulls)
