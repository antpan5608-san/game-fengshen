"""Synthetic transport fixtures test fail-closed release checks, never App proof."""
import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image
from tools import field_magic_evidence as magic


class FieldMagicEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        actors = [dict(id=name, level=12, experience=2000, hp=92, maxHp=92, mp=44,
            maxMp=44, strength=22, stamina=14, agility=26, spirit=43,
            equipment=dict(rightHand=19, leftHand=-1, body=11, feet=38))
            for name in ('nezha', 'xiaolongnv', 'yangjian', 'jiangziya')]
        actors[0].update(hp=5, maxHp=200)
        self.before = dict(saveSchemaVersion=1, contentVersion='opening-segment-001-c62',
            mapId=0, x=40, y=248, direction='UP', characters=actors, terrainMode=0,
            inventory={}, flags={}, money=10, encounterSteps=0)
        self.after = copy.deepcopy(self.before)
        self.after['characters'][0]['hp'] = 58
        self.after['characters'][1]['mp'] = 41
        self.write('touch-ux-world-field-magic-expected-save.json', self.after)
        self.write('world-field-magic-cold-boundary.json', dict(kind='ACTUAL_APP_EXTERNAL_COLD_BOUNDARY',
            before=self.after, after=self.after, differentTopLevelFields=[], equal=True))
        for font in ('1_0', '1_3', '2_0'):
            self.write('touch-ux-world-field-magic-font-' + font + '.json',
                dict(kind=magic.ACCEPTANCE['kind'], font=float(font.replace('_', '.')),
                    width=160, height=90, screenWidth=160, screenHeight=100,
                    selectionAndCancelUnchanged=True, repeatConfirmationUnchanged=True,
                    protectedSaveFailureRollbackUnchanged=True,
                    before=self.before, after=self.after))
            Image.new('RGB', (160, 100), (1, 2, 3)).save(
                self.folder / ('touch-ux-world-field-magic-committed-font-' + font + '.png'))
        for name in ('nezha-unavailable', 'selection', 'target', 'save-failure-rollback', 'cold-full-state'):
            Image.new('RGB', (160, 100), (1, 2, 3)).save(
                self.folder / ('touch-ux-world-field-magic-' + name + '.png'))
        segments = []
        for name in ('world-field-magic-normal-00.mp4', 'world-field-magic-cold-restart.mp4'):
            raw = b'SYNTHETIC_TRANSPORT_TEST_BYTES_NOT_APP_VIDEO'
            (self.folder / name).write_bytes(raw)
            segments.append(dict(file='artifacts/checkpoint-ui/' + name,
                sha256=hashlib.sha256(raw).hexdigest(), savedWorldBefore=self.after, savedWorldAfter=self.after))
        self.write('world-field-magic-recording.json', dict(
            source='Actual Android App screenrecord; SILENT, no sound validation',
            kind='CONTROLLED_FIELD_MAGIC_SMOKE', controlledAssertions='PASS', normalAssertions='NOT_APPLICABLE',
            forceStopRestartEqual=True, continuedExploration=False, originalPreferencesRestored=True, segments=segments))

    def write(self, name, value):
        (self.folder / name).write_text(json.dumps(value), encoding='utf-8')

    def mutate(self, name, mutation):
        value = json.loads((self.folder / name).read_text(encoding='utf-8'))
        mutation(value)
        self.write(name, value)

    def test_transport_fixture_and_raw_video_mutation(self):
        result = magic.proof_digests(self.folder)
        magic.validate_digests(result)
        (self.folder / 'world-field-magic-normal-00.mp4').write_bytes(b'CHANGED')
        with self.assertRaisesRegex(ValueError, 'bytes changed'):
            magic.proof_digests(self.folder)

    def test_claimed_cold_equality_cannot_hide_changed_state(self):
        self.mutate('world-field-magic-cold-boundary.json', lambda v: v['after'].update(money=99))
        with self.assertRaisesRegex(ValueError, 'full save differs'):
            magic.proof_digests(self.folder)

    def test_hp_mp_only_report_cannot_hide_missing_actor_fields(self):
        self.mutate('touch-ux-world-field-magic-font-1_3.json', lambda v: v['before']['characters'][0].pop('equipment'))
        with self.assertRaisesRegex(ValueError, 'complete actor'):
            magic.proof_digests(self.folder)

    def test_wrong_font_and_missing_screenshot_fail_closed(self):
        (self.folder / 'touch-ux-world-field-magic-committed-font-2_0.png').unlink()
        with self.assertRaisesRegex(ValueError, 'screenshot'):
            magic.proof_digests(self.folder)

    def test_unrelated_inventory_change_is_rejected(self):
        self.mutate('touch-ux-world-field-magic-font-1_3.json', lambda v: v['after']['inventory'].update(herb=1))
        with self.assertRaisesRegex(ValueError, 'unrelated snapshot'):
            magic.proof_digests(self.folder)
