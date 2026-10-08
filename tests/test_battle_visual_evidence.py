"""Synthetic byte/proof rejection tests do not assert an App run."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from tools import battle_visual_evidence as visual,runtime_handoff as h,battle_ui_evidence as ui
from tests import test_battle_magic_personal_scope as fixtures
from tests import test_battle_magic_evidence as magic_fixture
from PIL import Image


class VisualProofTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.evidence=Path(self.tmp.name);self.logs=self.evidence/'logs';self.logs.mkdir()
        (self.logs/'battle-visual-assets.txt').write_text('OK (1 test)\n')
        for name in ('normal-test','cold-start-test'):(self.evidence/f'world-visual-normal-{name}.txt').write_text('OK (1 test)\n')
        state={'contentVersion':'opening-segment-001-c62','money':31,'characters':[{'id':'nezha','hp':33}]}
        self.write('world-visual-normal-cold-boundary.json',dict(kind='ACTUAL_APP_EXTERNAL_COLD_BOUNDARY',before=state,after=state,equal=True,differentTopLevelFields=[]))
        segments=[]
        for name in ('world-visual-normal-normal-00.mp4','world-visual-normal-cold-restart.mp4'):
            raw=b'explicit synthetic bytes, not a real App video';(self.evidence/name).write_bytes(raw)
            segments.append(dict(file='artifacts/checkpoint-ui/'+name,sha256=hashlib.sha256(raw).hexdigest(),savedWorldBefore=state))
        segments[-1]['phase']='EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE'
        self.recording=dict(normalAssertions='PASS',forceStopRestartEqual=True,continuedExploration=True,originalPreferencesRestored=True,segments=segments,videos=[s['file']for s in segments])
        self.write('world-visual-normal-recording.json',self.recording)
        fixture=magic_fixture.BattleMagicEvidenceTest();fixture.setUp();self.addCleanup(fixture.doCleanups)
        phases=[('xiaolongnv','SPECIAL','CAST','xiaolongnv-cast.png',44,5),
                ('xiaolongnv','HEAL','CAST','xiaolongnv-cast.png',44,58),
                ('xiaolongnv','TEXT','IDLE','xiaolongnv-idle-v1.png',41,58),
                ('nezha','ATTACK','ATTACK','nezha-attack.png',41,58)]
        for font in ('1.0','1.3','2.0'):
            rows=[]
            for i,(actor,kind,pose,file,mp,hp) in enumerate(phases):
                name='touch-ux-world-visual-pose-'+font.replace('.','_')+'-'+str(i)+'.png'
                Image.new('RGB',(160,100),(1,2,3)).save(self.evidence/name)
                rows.append(dict(actor=actor,kind=kind,pose=pose,file=file,casterMP=mp,targetHP=hp,screenshot=name,
                    supportTarget='nezha' if kind in ('SPECIAL','HEAL') else None,arenaFlash=False))
                files=['nezha-attack.png' if kind=='ATTACK' else 'nezha-idle-v1.png',
                    'xiaolongnv-cast.png' if kind in ('SPECIAL','HEAL') else 'xiaolongnv-idle-v1.png',
                    'yangjian-idle-v2.png','jiangziya-idle-v1.png']
                rows[-1]['partyBodies']=[dict(actor=a,file=f,x=10+j*30,y=20,w=20,h=40)
                    for j,(a,f)in enumerate(zip(('nezha','xiaolongnv','yangjian','jiangziya'),files))]
            self.write('touch-ux-world-visual-poses-'+font+'.json',dict(
                kind='CONTROLLED_REAL_ACTION_QUEUE_VISUAL_ONLY_NOT_NORMAL_JOIN_OR_PHONE',font=float(font),
                manifestSha256=visual.ACCEPTANCE['manifestSha256'],prepared=12,decodedBytes=32*1024*1024,
                startupPrepared=4,preparedFiles=list(visual.POSE_FILES),**{k:True for k in visual.DELIVERY_GUARDS},
                partySpacing=visual.ACCEPTANCE['partySpacing'],projectedSpacingSamples=36,
                renderStateUnchanged=True,rngUnchanged=True,before=fixture.before,after=fixture.before,
                screenWidth=160,screenHeight=100,phases=rows))
            (self.logs/('testControlledBattleVisualPosesReadOnly-font-'+font+'.txt')).write_text('OK (1 test)\n')

    def write(self,name,value):(self.evidence/name).write_text(json.dumps(value),encoding='utf-8')
    def proof(self):return visual.proof_digests(self.evidence,self.logs)

    def test_party_spacing_refuses_old_report_overlap_wrong_actor_and_invalid_coordinates(self):
        name='touch-ux-world-visual-poses-1.0.json';original=json.loads((self.evidence/name).read_text())
        changes=[]
        for key,value in [('partySpacing',None),('projectedSpacingSamples',True),('projectedSpacingSamples',0)]:
            bad=copy.deepcopy(original);bad[key]=value;changes.append(bad)
        for key,value in [('x',40),('w',0),('x',float('nan')),('x',True),('actor','unknown'),('file','enemy-1.png')]:
            bad=copy.deepcopy(original);bad['phases'][0]['partyBodies'][0][key]=value;changes.append(bad)
        bad=copy.deepcopy(original);bad['phases'][0].pop('partyBodies');changes.append(bad)
        for bad in changes:
            self.write(name,bad)
            with self.assertRaises(ValueError):self.proof()
        self.write(name,original);self.assertEqual(64,len(self.proof()[visual.PROOF_KEYS[0]]))

    def test_complete_raw_binding_and_changed_video_rejected(self):
        self.assertEqual(64,len(self.proof()[visual.PROOF_KEYS[0]]))
        (self.evidence/'world-visual-normal-normal-00.mp4').write_bytes(b'changed')
        with self.assertRaises(ValueError):self.proof()

    def test_missing_decoder_failure_log_controlled_or_false_cold_rejected(self):
        for change in (dict(controlledAssertions='PASS'),dict(normalAssertions='NOT_APPLICABLE'),dict(forceStopRestartEqual=False),dict(originalPreferencesRestored=False)):
            self.write('world-visual-normal-recording.json',dict(self.recording,**change))
            with self.assertRaises(ValueError):self.proof()
        self.write('world-visual-normal-recording.json',self.recording)
        (self.logs/'battle-visual-assets.txt').write_text('FAILURES!!!\nOK (1 test)\n')
        with self.assertRaises(ValueError):self.proof()
        (self.logs/'battle-visual-assets.txt').unlink()
        with self.assertRaises(ValueError):self.proof()

    def test_changed_full_snapshot_and_escaping_video_path_rejected(self):
        path=self.evidence/'world-visual-normal-cold-boundary.json';value=json.loads(path.read_text());value['after']['money']=999
        self.write(path.name,value)
        with self.assertRaises(ValueError):self.proof()
        value['before']=copy.deepcopy(value['after']);self.write(path.name,value)
        recording=copy.deepcopy(self.recording);recording['segments'][0]['file']='private-inputs/save.mp4';recording['videos'][0]='private-inputs/save.mp4'
        self.write('world-visual-normal-recording.json',recording)
        with self.assertRaises(ValueError):self.proof()

    def test_new_gates_keep_all_thirty_three_old_gates_and_refuse_omitted_visual_proofs(self):
        fixture=fixtures.BattleMagicPersonalScopeTest();fixture.setUp();self.addCleanup(fixture.doCleanups)
        scope=copy.deepcopy(fixture.scope);scope['battleVisualAcceptance']=visual.ACCEPTANCE;scope['personalTest']['gates']+=visual.GATES
        scope['battleUiAcceptance']=ui.acceptance(require_insets=True,require_feedback=True)
        fixture.fixture.fixture.write_scope(scope)
        candidate=dict(fixture.candidate,versionCode=92);proposed=dict(fixture.proposed,versionCode=92,**{g:'PASS'for g in visual.GATES},**self.proof())
        self.assertEqual(36,len(h.personal_gates(h.active_scope(candidate))))
        h.review_personal(h.finish_personal(proposed))
        bad_scope=copy.deepcopy(scope);bad_scope['battleUiAcceptance'].pop('geometryEvidence')
        fixture.fixture.fixture.write_scope(bad_scope)
        with self.assertRaisesRegex(ValueError,'battle UI'):h.active_scope(candidate)
        fixture.fixture.fixture.write_scope(scope)
        for key in (*visual.GATES,*visual.PROOF_KEYS):
            bad=dict(proposed);bad.pop(key)
            with self.assertRaises(ValueError):h.finish_personal(bad)
        scope.pop('battleVisualAcceptance');scope['personalTest']['gates']=[g for g in scope['personalTest']['gates']if g not in visual.GATES]
        fixture.fixture.fixture.write_scope(scope)
        with self.assertRaisesRegex(ValueError,'visual'):h.active_scope(candidate)

    def test_pose_report_wrong_mp_draw_mutation_missing_font_picture_and_test_rejected(self):
        name='touch-ux-world-visual-poses-1.3.json';original=json.loads((self.evidence/name).read_text())
        for key in ('renderStateUnchanged','rngUnchanged'):
            self.write(name,dict(original,**{key:False}))
            with self.assertRaises(ValueError):self.proof()
        changed=copy.deepcopy(original);changed['phases'][1]['casterMP']=41;self.write(name,changed)
        with self.assertRaisesRegex(ValueError,'timing'):self.proof()
        changed=copy.deepcopy(original);changed['after']['money']=77;self.write(name,changed)
        with self.assertRaises(ValueError):self.proof()
        self.write(name,original)
        path=self.logs/'testControlledBattleVisualPosesReadOnly-font-1.3.txt';path.write_text('FAILURES!!!\nOK (1 test)\n')
        with self.assertRaises(ValueError):self.proof()
        path.write_text('OK (1 test)\n');(self.evidence/original['phases'][0]['screenshot']).unlink()
        with self.assertRaises(ValueError):self.proof()

    def test_support_target_global_flash_and_old_reports_are_rejected(self):
        name='touch-ux-world-visual-poses-1.3.json';original=json.loads((self.evidence/name).read_text())
        for key,value in [('supportTarget','xiaolongnv'),('arenaFlash',True),('arenaFlash',0)]:
            changed=copy.deepcopy(original);changed['phases'][0][key]=value;self.write(name,changed)
            with self.assertRaisesRegex(ValueError,'Support feedback'):self.proof()
        changed=copy.deepcopy(original)
        for phase in changed['phases']:phase.pop('supportTarget');phase.pop('arenaFlash')
        self.write(name,changed)
        with self.assertRaisesRegex(ValueError,'Support feedback'):self.proof()

    def test_full_preload_missing_scope_and_stale_delivery_are_rejected(self):
        name='touch-ux-world-visual-poses-1.3.json';original=json.loads((self.evidence/name).read_text())
        for change in (dict(prepared=16),dict(startupPrepared=16),dict(preparedFiles=list(visual.POSE_FILES)+['enemy-137.png']),
                *({k:False}for k in visual.DELIVERY_GUARDS),*({k:1}for k in visual.DELIVERY_GUARDS)):
            self.write(name,dict(original,**change))
            with self.assertRaises(ValueError):self.proof()
        changed=copy.deepcopy(original);changed.pop('startupPrepared');self.write(name,changed)
        with self.assertRaisesRegex(ValueError,'Scoped preparation'):self.proof()


if __name__=='__main__':unittest.main()
