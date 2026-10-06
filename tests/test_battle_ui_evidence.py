"""Synthetic artifact rejection fixtures; these are not App or phone validation."""
import copy
import io
import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image
from tools import battle_ui_evidence as ui


class BattleUiEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.evidence = self.root / 'checkpoint-ui'; self.evidence.mkdir()
        self.logs = self.root / 'town02-runtime'; self.logs.mkdir()
        buf = io.BytesIO(); Image.new('RGB', (2640, 1216)).save(buf, format='PNG')
        self.png = buf.getvalue()
        for name in ui.log_names(): (self.logs / name).write_text('Synthetic fixture only\nOK (1 test)\n')
        for font in ui.FONTS:
            metrics = dict(kind='CONTROLLED_LAYOUT_EMULATOR_NOT_REAL_PHONE', screenWidth=2640, screenHeight=1216,
                           windowWidth=2640, windowHeight=1080, density=3, fontScale=float(font),
                           minTouchDp=48, medicineTextRowsVisible=3)
            self.write('mobile-phone-' + font + '.json', metrics)
            native = dict(metrics, kind='CONTROLLED_NATIVE_LAYOUT_EMULATOR_NOT_REAL_PHONE',
                          fourActorInputAndRewardChecks='PASS', cases=[])
            for count in range(1, 5):
                for enemy, group in (([35] * 6, 8), ([137], 156)):
                    cards = [dict(x=24+i%2*1302, y=420+i//2*168, width=1290, height=156) for i in range(count)]
                    native['cases'].append(dict(party=list(ui.PARTY[:count]), groupId=group, enemies=enemy,
                        frame=dict(x=24, y=24, width=2592, height=888), cards=cards,
                        enemyField=dict(x=24, y=132, width=1540, height=264),
                        allyField=dict(x=1588, y=132, width=1027, height=264), compactInstanceNumbers=len(enemy)==6))
            self.write('mobile-party-phone-' + font + '.json', native)
            for name in ui.screenshot_names(font): (self.evidence / name).write_bytes(self.png)

    def write(self, name, value):
        (self.evidence / name).write_text(json.dumps(value), encoding='utf-8')

    def proof(self): return ui.proof_digests(self.evidence, self.logs)

    def test_exact_artifact_set_digest_binds_all_raw_bytes(self):
        original = self.proof()
        self.assertEqual({ui.UI_PROOF_KEY}, set(original))
        self.assertEqual(64, len(original[ui.UI_PROOF_KEY]))
        image = self.evidence / ui.screenshot_names('2.0')[0]
        Image.new('RGB', (2640, 1216), 'red').save(image)
        self.assertNotEqual(original, self.proof())

    def test_missing_corrupt_or_wrong_sized_actual_screenshot_is_rejected(self):
        path = self.evidence / ui.screenshot_names('1.0')[0]
        for data in (b'invalid PNG', self.png[:40]):
            path.write_bytes(data)
            with self.subTest(data=data[:8]), self.assertRaises(ValueError): self.proof()
        Image.new('RGB', (960, 540)).save(path)
        with self.assertRaises(ValueError): self.proof()
        path.unlink()
        with self.assertRaises(ValueError): self.proof()

    def test_every_real_method_log_must_pass_before_creating_a_digest(self):
        for name in ui.log_names():
            path = self.logs / name; original = path.read_bytes()
            path.write_text('OK (1 test)\nFAILURES!!!\n')
            with self.subTest(name=name), self.assertRaises(ValueError): self.proof()
            path.write_bytes(original)
        path = self.logs / ui.log_names()[-1]
        path.write_text('OK (2 tests)\n')
        with self.assertRaises(ValueError): self.proof()
        path.unlink()
        with self.assertRaises(ValueError): self.proof()

    def test_actual_font_window_party_geometry_and_all_native_cases_are_required(self):
        name = 'mobile-party-phone-2.0.json'
        original = json.loads((self.evidence / name).read_text())
        for mutation in ('font', 'window', 'case', 'actor', 'enemy', 'region', 'overlap', 'number', 'claims'):
            value = copy.deepcopy(original)
            if mutation == 'font': value['fontScale'] = 1.3
            elif mutation == 'window': value['windowHeight'] = 1216
            elif mutation == 'case': value['cases'].pop()
            elif mutation == 'actor': value['cases'][6]['party'].pop()
            elif mutation == 'enemy': value['cases'][6]['enemies'][0] = 137
            elif mutation == 'region': value['cases'][6]['cards'][0]['height'] = 100
            elif mutation == 'overlap': value['cases'][6]['cards'][1] = value['cases'][6]['cards'][0]
            elif mutation == 'number': value['cases'][6]['compactInstanceNumbers'] = False
            else: value['kind'] = 'REAL_PHONE_PASS'
            self.write(name, value)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.proof()
        self.write(name, original)
        old = json.loads((self.evidence / 'mobile-phone-2.0.json').read_text())
        old['medicineTextRowsVisible'] = 2
        self.write('mobile-phone-2.0.json', old)
        with self.assertRaises(ValueError): self.proof()


if __name__ == '__main__': unittest.main()
