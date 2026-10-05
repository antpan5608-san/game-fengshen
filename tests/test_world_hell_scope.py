"""Preserved full data, finite same-candidate hell/rebirth validation; no App PASS claim."""
import copy, hashlib, json, os, subprocess, sys, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
from tools import runtime_handoff as h
from tests.test_runtime_handoff import existing_bash

ROOT=Path(__file__).resolve().parents[1]

class HellScopeTest(unittest.TestCase):
    def setUp(self):
        self.scope=h.active_scope()
        self.candidate=dict(sourceCommit='c'*40,buildRunID='123',sha256='a'*64,
            contentHash=self.scope['manifestSha256'],contentVersion=self.scope['contentVersion'],
            versionCode=81,versionName='fixture-only',signerSha256='d'*64)
        self.proposed=dict(self.candidate,**{k:'PASS' for v in self.scope['stageGates'].values() for k in v})

    def test_development_data_retained_and_reproducible_from_original_base(self):
        sys.path.insert(0,str(ROOT/'tools'))
        import ci_apk as ci, export_development as ex
        pin=ex.load(ROOT/'ci/content-source.json')
        base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK',
            '/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),pin['iteration']['base'])
        payload=ex.export_from_base(base,pin['iteration']['provenance'],pin)
        self.assertEqual(302,len(payload))
        self.assertEqual(pin['manifestSha256'],hashlib.sha256(payload['manifest.json']).hexdigest())
        scene=json.loads(payload['scene.json'])
        self.assertEqual(self.scope['mapIds'],sorted(m['id'] for m in scene['maps']))
        self.assertEqual(56,len(scene['maps']))
        self.assertTrue({116,117,141,74,100,164}.issubset(self.scope['mapIds']))
        self.assertEqual({1,2,3,4,5,6},{c['callerMapId'] for c in scene['clinics']})
        self.assertEqual(payload,ex.export_from_base(base,pin['iteration']['provenance'],pin))
        ci.validate_item_sources(payload)

    def test_inventory_uses_target_package_instead_of_historical_base_maps(self):
        sys.path.insert(0,str(ROOT/'tools'))
        import export_development as ex
        pin=ex.load(ROOT/'ci/content-source.json')
        path=Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK',
            '/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk'))
        scene,source=ex.inventory_target_from_base(path,pin)
        self.assertEqual(self.scope['mapIds'],sorted(m['id'] for m in scene['maps']))
        self.assertEqual((302,27,'NOT_RUN','NOT_ASSESSED'),
            (source['fileCount'],source['baseVersionCode'],source['appRuntime'],source['publication']))
        self.assertEqual(pin['manifestSha256'],source['manifestSha256'])
        self.assertEqual('REPRODUCIBLE_TARGET_EXPORT_NOT_RELEASE',source['kind'])
        report={};ex.attach_inventory_package(report,scene,source)
        bindings=report['packagedServiceBindings']
        self.assertEqual(40,len(bindings))
        self.assertEqual(set(range(7)),{b['callerMapId'] for b in bindings})
        self.assertEqual(12,sum(b['definitionKind']=='clinic' for b in bindings))
        self.assertTrue(all(b['packaged'] and b['appVerification']=='NOT_RUN'
            and b['publication']=='NOT_ASSESSED' for b in bindings))
        wrong=copy.deepcopy(scene);wrong['serviceBindings'][0]['shopId']='unimplemented'
        with self.assertRaisesRegex(ValueError,'unresolved target'):
            ex.attach_inventory_package({},wrong,source)
        with patch.object(ex,'digest',return_value='f'*64),self.assertRaisesRegex(ValueError,'Wrong reviewed inventory APK'):
            ex.inventory_target_from_base(path,pin)

    def test_same_candidate_three_stages_and_all_actual_gates_required(self):
        base=h.finish_stage('base',self.proposed)
        world=h.finish_stage('world',self.proposed,base)
        final=h.finish_stage('continuation',self.proposed,world)
        self.assertEqual('PARTIAL',world['runtime'])
        self.assertEqual('PASS',final['runtime'])
        self.assertEqual(list(h.STAGES),final['completedStages'])
        for k in self.scope['deferredFullWorldGates']:
            self.assertNotIn(k,final)
        for stage,previous in [('world',None),('continuation',base)]:
            with self.assertRaises(ValueError):h.finish_stage(stage,self.proposed,previous)
        for stage,previous in [('base',None),('world',base),('continuation',world)]:
            for key in self.scope['stageGates'][stage]:
                with self.subTest(stage=stage,gate=key),self.assertRaises(ValueError):
                    h.finish_stage(stage,dict(self.proposed,**{key:'NOT_RUN'}),previous)
        with self.assertRaises(ValueError):
            h.finish_stage('continuation',dict(self.proposed,sha256='f'*64),world)

    def test_rebirth_endpoint_and_normal_handoff_cannot_be_weakened(self):
        original=h.read_json
        for field in ['map','endpoint','gate','checkpoint','quality','jobs']:
            s=copy.deepcopy(self.scope)
            if field=='map':s['mapIds'].remove(116)
            elif field=='endpoint':s['endpoint']['mapId']=2
            elif field=='gate':s['stageGates']['continuation'].remove('worldRebirthDialogueAndColdRestart')
            elif field=='checkpoint':s['checkpoints']['world']=['hell-village2']
            elif field=='quality':s['quality']='PERSONAL_TEST'
            else:s['requiredJobs'].remove('runtime-world')
            with patch.object(h,'read_json',side_effect=lambda p:s if p==h.SCOPE_PATH else original(p)),self.assertRaises(ValueError):
                h.active_scope()

    def test_original_dispatch_runs_only_frozen_hell_batch_and_preserves_future_modes(self):
        script=(ROOT/'ci/run-town02-runtime.sh').read_text()
        start=script.index('if [[ "$stage" == all || "$stage" == base ]]; then\nrun_test testUpgradeKeepsPreviousSave')
        block=script[start:script.index('# Clinical sub-results',start)]
        prefix='''set -euo pipefail
scope_id=WORLD-HELL-R2
quality=STABLE
python(){ printf 'PY %s\\n' "$*"; [[ "$*" != *"${FAIL_FLOW:-never-fail}"* ]]; }
run_test(){ :; }
adb(){ :; }
sleep(){ :; }
pull_evidence(){ :; }
'''
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'hell.sh';path.write_text(prefix+block,encoding='utf-8',newline='\n')
            (Path(td)/'artifacts/town02-runtime').mkdir(parents=True)
            for stage in ('base','world','continuation'):
                result=subprocess.run([existing_bash(),str(path)],env=dict(os.environ,stage=stage),capture_output=True,text=True,timeout=10,cwd=td)
                self.assertEqual(0,result.returncode,result.stderr)
                lines=[line for line in result.stdout.splitlines() if 'record_app_audio.py' in line]
                flows=[line.split('record_app_audio.py ',1)[1].split()[0] for line in lines]
                expected={'base':['touch-ux-after','world-f0','nanhai-ci','world-west','world-north-palace','world-north','world-village1'],
                    'world':['world-cave85','world-east-palace','world-hell-village2','world-r1-medical','world-first-hall','world-second-hall','world-hall-batch'],
                    'continuation':['world-rebirth']}[stage]
                self.assertEqual(expected,flows)
                self.assertNotIn('world-queen117',flows)
            fail=subprocess.run([existing_bash(),str(path)],env=dict(os.environ,stage='world',FAIL_FLOW='world-first-hall'),capture_output=True,text=True,timeout=10,cwd=td)
            self.assertNotEqual(0,fail.returncode)
            self.assertNotIn('record_app_audio.py world-second-hall',fail.stdout)
        self.assertIn('world-queen117 testNormalQueenRouteBindingAndHuang',script)

    def test_review_command_rejects_missing_gates_and_unexecuted_future_claims(self):
        final=h.finish_stage('continuation',self.proposed,h.finish_stage('world',self.proposed,h.finish_stage('base',self.proposed)))
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'receipt.json'
            for value,ok in [(final,True),(dict(final,worldFinalHallsNormal='NOT_RUN'),False),
                    (dict(final,worldQueenRouteAndBindingNormal='PASS'),False),
                    (dict(final,completedStages=['base','continuation']),False),
                    (dict(final,contentHash='f'*64),False)]:
                p.write_text(json.dumps(value))
                run=subprocess.run([sys.executable,str(ROOT/'tools/runtime_handoff.py'),'review','--receipt',str(p)],capture_output=True,text=True,timeout=10)
                self.assertEqual(ok,run.returncode==0,run.stderr[-500:])

    def test_medical_endpoint_is_the_real_first_hall_source_and_content_methods_exist(self):
        touch=(ROOT/'android/app/src/androidTest/java/org/fengshen/dev/TouchTest.kt').read_text()
        self.assertIn('normalSourceName="world-r1-medical-expected-save.json"',touch)
        self.assertIn('steps++<10000',touch)
        self.assertIn('never grant levels/money',touch)
        content=(ROOT/'android/app/src/androidTest/java/org/fengshen/dev/ContentTest.kt').read_text()
        for method in self.scope['contentTests']:self.assertIn('fun '+method+'(',content)
        checker=(ROOT/'ci/check-reviewed-apk.ps1').read_text()
        self.assertIn("@('PLAYABLE-R1','WORLD-HELL-R2')",checker)
        self.assertIn("'base,world,continuation'",checker)

if __name__=='__main__':unittest.main()
