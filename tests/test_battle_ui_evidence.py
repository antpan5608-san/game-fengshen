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
    def add_feedback_fixture(self,value,native):
        def row(enemy,slot,compact):
            cell=dict(x=24+slot*250 if compact else 24,y=132,width=240 if compact else 1540,height=264)
            sprite=dict(x=cell['x']+20,y=210,width=60 if compact else 100,height=100)
            label=dict(x=cell['x']+10 if compact else sprite['x']+sprite['width']+24,y=140 if compact else 180,width=124,height=60)
            gauge=dict(x=label['x'],y=cell['y']+240 if compact else 246,width=124,height=12)
            return dict(enemyId=enemy,slot=slot,cell=cell,sprite=sprite,label=label,gauge=gauge,
                text=f'#{slot+1}'if compact else '南海龙王',textWidth=60,textHeight=42,
                fontSp=12,density=3,adapted=not compact,originalSpritePreserved=True,stateUnchanged=True)
        value['enemyFeedbackModel']=ui.FEEDBACK_EVIDENCE
        if native:
            for case in value['cases']:
                enemies=case['enemies'];case['enemyFeedback']=[row(e,i if len(enemies)==6 else 3,len(enemies)==6)for i,e in enumerate(enemies)]
        else:value['enemyFeedback']=[row(137,3,False)]
        return value

    def test_measured_feedback_is_hash_bound_and_old_or_forged_layouts_rejected(self):
        for font in ui.FONTS:
            for prefix,native in (('mobile-phone-',False),('mobile-party-phone-',True)):
                name=prefix+font+'.json';v=json.loads((self.evidence/name).read_text())
                self.write(name,self.add_feedback_fixture(v,native))
        proof=ui.proof_digests(self.evidence,self.logs,require_feedback=True)
        self.assertEqual(64,len(proof[ui.UI_PROOF_KEY]))
        name='mobile-party-phone-2.0.json';original=json.loads((self.evidence/name).read_text())
        for change in ('missing','model','slot','enemy','overlap','escape','nan','bool','font','state','wide','compact'):
            v=copy.deepcopy(original);row=v['cases'][1]['enemyFeedback'][0]
            if change=='missing':v['cases'][1].pop('enemyFeedback')
            elif change=='model':v.pop('enemyFeedbackModel')
            elif change=='slot':row['slot']=0
            elif change=='enemy':row['enemyId']=35
            elif change=='overlap':row['label']['x']=row['sprite']['x']
            elif change=='escape':row['gauge']['x']=2600
            elif change=='nan':row['textWidth']=float('nan')
            elif change=='bool':row['textHeight']=True
            elif change=='font':row['fontSp']=11
            elif change=='state':row['stateUnchanged']=1
            elif change=='wide':row['label']['width']=row['gauge']['width']=900
            else:v['cases'][0]['enemyFeedback'][0]['text']='new name'
            self.write(name,v)
            with self.subTest(change=change),self.assertRaises(ValueError):ui.proof_digests(self.evidence,self.logs,require_feedback=True)
        self.write(name,original)
        self.assertEqual(proof,ui.proof_digests(self.evidence,self.logs,require_feedback=True))

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

    def test_current_candidate_requires_measured_insets_for_both_surface_modes(self):
        with self.assertRaisesRegex(ValueError,'inset'):ui.proof_digests(self.evidence,self.logs,require_insets=True)
        for height in (1080,1216):
            for font in ui.FONTS:
                for prefix in ('mobile-phone-','mobile-party-phone-'):
                    name=prefix+font+'.json';value=json.loads((self.evidence/name).read_text())
                    value.update(windowHeight=height,geometryEvidence=ui.GEOMETRY_EVIDENCE,
                        safeInsets=dict(left=0,top=0,right=0,bottom=144),
                        safeArea=dict(x=0,y=0,width=2640,height=height-144))
                    self.write(name,value)
            self.assertEqual(64,len(ui.proof_digests(self.evidence,self.logs,require_insets=True)[ui.UI_PROOF_KEY]))

    def test_measured_surface_rejects_forged_insets_and_escaping_frame(self):
        name='mobile-party-phone-2.0.json';base=json.loads((self.evidence/name).read_text())
        base.update(windowHeight=1216,geometryEvidence=ui.GEOMETRY_EVIDENCE,
            safeInsets=dict(left=0,top=0,right=0,bottom=144),safeArea=dict(x=0,y=0,width=2640,height=1072))
        ui.validate_metrics(base,'2.0',True,require_insets=True)
        for change in ('protocol','missing','inset','safe','height','frame'):
            value=copy.deepcopy(base)
            if change=='protocol':value['geometryEvidence']='INVENTED'
            elif change=='missing':value.pop('safeInsets')
            elif change=='inset':value['safeInsets']['bottom']=True
            elif change=='safe':value['safeArea']['height']=1216
            elif change=='height':value['windowHeight']=1217
            else:value['cases'][0]['frame']['height']=1080
            with self.subTest(change=change),self.assertRaises(ValueError):ui.validate_metrics(value,'2.0',True,require_insets=True)


if __name__ == '__main__': unittest.main()
