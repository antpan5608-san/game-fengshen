"""Synthetic byte/proof rejection tests do not assert an App run."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from tools import battle_visual_evidence as visual,runtime_handoff as h
from tests import test_battle_magic_personal_scope as fixtures


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

    def write(self,name,value):(self.evidence/name).write_text(json.dumps(value),encoding='utf-8')
    def proof(self):return visual.proof_digests(self.evidence,self.logs)

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
        fixture.fixture.fixture.write_scope(scope)
        candidate=dict(fixture.candidate,versionCode=91);proposed=dict(fixture.proposed,versionCode=91,**{g:'PASS'for g in visual.GATES},**self.proof())
        self.assertEqual(35,len(h.personal_gates(h.active_scope(candidate))))
        h.review_personal(h.finish_personal(proposed))
        for key in (*visual.GATES,*visual.PROOF_KEYS):
            bad=dict(proposed);bad.pop(key)
            with self.assertRaises(ValueError):h.finish_personal(bad)
        scope.pop('battleVisualAcceptance');scope['personalTest']['gates']=[g for g in scope['personalTest']['gates']if g not in visual.GATES]
        fixture.fixture.fixture.write_scope(scope)
        with self.assertRaisesRegex(ValueError,'visual'):h.active_scope(candidate)


if __name__=='__main__':unittest.main()
