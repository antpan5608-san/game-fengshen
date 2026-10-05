"""Frozen R1 dependencies and exact same-candidate stage gates; no gameplay claim."""
import copy,hashlib,json,os,subprocess,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from tools import runtime_handoff as h
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci,export_development as ex


class PlayableR1ScopeTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.scope=h.active_scope();cls.pin=ex.load(ci.ROOT/'ci/content-source.json')
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK',
   '/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
  cls.out=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin)
  cls.scene=json.loads(cls.out['scene.json'])
 def test_clean_recipe_exact_dependencies_unchanged_media_and_no_future_boss(self):
  self.assertEqual((120,18),(len(self.out),len(self.scene['maps'])))
  self.assertEqual(self.scope['mapIds'],sorted(m['id']for m in self.scene['maps']))
  self.assertEqual(self.out,ex.export_from_base(self.base,self.pin['iteration']['provenance'],self.pin))
  parent=ex.load(ci.ROOT/'ci/golden-world-village2-content.json')
  old=ex.export_from_base(self.base,parent['iteration']['provenance'],parent)
  for name,raw in old.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.out[name],name)
  ci.validate_item_sources(self.out)
  for name,sha in json.loads(self.out['manifest.json'])['files'].items():self.assertEqual(sha,ci.sha(self.out[name]))
  self.assertFalse(set(self.scope['mapIds'])&{60,61,62,63,64,65,66,67,68,76,86,87,115,116,117})
  self.assertFalse(any(n['mapId']in {60,61,62,63,64,65,66,67,68,115,116,117}for n in self.scene['npcs']))
  self.assertEqual({1,2},{c['callerMapId']for c in self.scene['clinics']})
  self.assertEqual(2,len([n for n in self.scene['npcs']if n.get('clinicId')]))
 def test_frozen_scope_cannot_drop_required_gate_or_change_endpoint_content(self):
  original=h.read_json
  for field in ['gate','map','endpoint','hash','jobs']:
   scope=copy.deepcopy(self.scope)
   if field=='gate':scope['stageGates']['base'].remove('upgrade')
   elif field=='map':scope['mapIds'].append(116)
   elif field=='endpoint':scope['endpoint']['mapId']=117
   elif field=='hash':scope['manifestSha256']='f'*64
   else:scope['requiredJobs'].remove('runtime-world')
   def read(path):return scope if path==h.SCOPE_PATH else original(path)
   with patch.object(h,'read_json',side_effect=read),self.assertRaises(ValueError,msg=field):h.active_scope()
 def test_three_actual_stages_never_mark_deferred_world_flows_passed(self):
  proposed=dict(sourceCommit='c'*40,buildRunID='123',sha256='a'*64,contentHash=self.pin['manifestSha256'],
   contentVersion=self.pin['contentVersion'],versionCode=68,versionName='fixture-only',signerSha256=self.pin['signerSha256'])
  proposed.update({k:'PASS'for k in h.R1_BASE_KEYS+h.WORLD_KEYS+h.CONTINUATION_KEYS+h.R1_CONTINUATION_KEYS})
  base=h.finish_stage('base',proposed);self.assertEqual('PARTIAL',base['runtime'])
  world=h.finish_stage('world',proposed,base);self.assertEqual('PARTIAL',world['runtime'])
  final=h.finish_stage('continuation',proposed,world);self.assertEqual('PASS',final['runtime'])
  for key in self.scope['deferredFullWorldGates']:self.assertNotIn(key,final)
  for stage,previous in [('world',None),('continuation',base)]:
   with self.assertRaises(ValueError):h.finish_stage(stage,proposed,previous)
  bad=dict(proposed,upgrade='FAIL')
  with self.assertRaises(ValueError):h.finish_stage('base',bad)
  with self.assertRaises(ValueError):h.finish_stage('all',proposed)
 def test_actual_review_command_accepts_complete_scope_and_rejects_missing_or_future_claims(self):
  if self.scope.get('quality')=='PERSONAL_TEST':
   self._personal_review_command_cases();return
  candidate=dict(sourceCommit='c'*40,buildRunID='123',sha256='a'*64,contentHash=self.pin['manifestSha256'],
   contentVersion=self.pin['contentVersion'],versionCode=68,versionName='fixture-only',signerSha256=self.pin['signerSha256'])
  candidate.update({key:'PASS'for keys in h.R1_STAGE_GATES.values()for key in keys})
  receipt=h.finish_stage('continuation',candidate,h.finish_stage('world',candidate,h.finish_stage('base',candidate)))
  cases=[(receipt,True),(dict(receipt,worldHellVillageNormal='FAIL'),False),
   (dict(receipt,completedStages=['base','continuation']),False),
   (dict(receipt,worldQueenRouteAndBindingNormal='PASS'),False),
   (dict(receipt,contentHash='f'*64),False)]
  with tempfile.TemporaryDirectory()as td:
   path=Path(td)/'isolated-runtime-receipt.json'
   for value,ok in cases:
    path.write_text(json.dumps(value),encoding='utf-8')
    run=subprocess.run([sys.executable,str(ci.ROOT/'tools/runtime_handoff.py'),'review','--receipt',str(path)],
     capture_output=True,text=True,timeout=10)
    self.assertEqual(ok,run.returncode==0,run.stderr[-500:])
 def _personal_review_command_cases(self):
  candidate=dict(sourceCommit='c'*40,buildRunID='123',sha256='a'*64,contentHash=self.pin['manifestSha256'],
   contentVersion=self.pin['contentVersion'],versionCode=78,versionName='fixture-only',signerSha256=self.pin['signerSha256'])
  candidate.update({key:'PASS' for key in h.PERSONAL_GATES})
  receipt=h.finish_personal(candidate)
  cases=[(receipt,True),(dict(receipt,externalColdRestart='NOT_RUN'),False),
   (dict(receipt,completedStages=['base','world','continuation']),False),
   (dict(receipt,worldQueenRouteAndBindingNormal='PASS'),False),(dict(receipt,manual_acceptance='PASS'),False),
   (dict(receipt,contentHash='f'*64),False)]
  with tempfile.TemporaryDirectory() as td:
   path=Path(td)/'receipt.json'
   for value,ok in cases:
    path.write_text(json.dumps(value),encoding='utf-8')
    run=subprocess.run([sys.executable,str(ci.ROOT/'tools/runtime_handoff.py'),'review','--receipt',str(path)],capture_output=True,text=True,timeout=10)
    self.assertEqual(ok,run.returncode==0,run.stderr[-500:])
 def test_stage_content_methods_exist_and_shared_regressions_remain(self):
  source=(ci.ROOT/'android/app/src/androidTest/java/org/fengshen/dev/ContentTest.kt').read_text(encoding='utf-8')
  for name in self.scope['contentTests']:self.assertIn('fun '+name+'(',source)
  script=(ci.ROOT/'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
  self.assertIn('testUpgradeKeepsPreviousSave',script)
  self.assertIn('testNormalWorldFirstHallFromVerifiedHellVillageSave',script)
  self.assertRegex(script,r'world-queen117 testNormal[A-Za-z0-9]+')
  self.assertIn('testNormalPlayableR1MedicalFromVerifiedVillageSave',script)

if __name__=='__main__':unittest.main()
