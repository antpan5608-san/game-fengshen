"""Explicit personal tier rejects weakened checks and false stable/manual claims."""
import copy,json,subprocess,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
from tools import runtime_handoff as h

ROOT=Path(__file__).resolve().parents[1]

class PersonalDeliveryTest(unittest.TestCase):
    def setUp(self):
        self.source_scope=h.active_scope()
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        fixture=Path(self.tmp.name)/'runtime-scope.json'
        scope=json.loads((ROOT/'ci/golden-playable-r1-scope.json').read_text()) if self.source_scope['id']!='PLAYABLE-R1' else copy.deepcopy(self.source_scope)
        scope['quality']='PERSONAL_TEST'
        fixture.write_text(json.dumps(scope))
        pin=json.loads((ROOT/('ci/golden-playable-r1-content.json' if self.source_scope['id']!='PLAYABLE-R1' else 'ci/content-source.json')).read_text())
        pin['runtimeScope']['sha256']=h.digest(fixture)
        (fixture.parent/'content-source.json').write_text(json.dumps(pin))
        scope_patch=patch.object(h,'SCOPE_PATH',fixture);scope_patch.start();self.addCleanup(scope_patch.stop)
        self.scope=h.active_scope()
        self.candidate=dict(sourceCommit='c'*40,buildRunID='123',sha256='a'*64,
            contentHash=self.scope['manifestSha256'],contentVersion=self.scope['contentVersion'],
            versionCode=78,versionName='fixture-only',signerSha256='d'*64)
        self.proposed=dict(self.candidate,**{key:'PASS' for key in h.PERSONAL_GATES})

    def test_actual_personal_minimum_does_not_claim_stable_or_long_acceptance(self):
        receipt=h.finish_personal(self.proposed)
        h.review_personal(receipt)
        self.assertEqual('SMOKE_PASS',receipt['runtime'])
        self.assertEqual('PENDING',receipt['manual_acceptance'])
        self.assertEqual('NOT_RUN',receipt['stableAcceptance'])
        self.assertEqual(['personal-smoke'],receipt['completedStages'])
        self.assertFalse(any(receipt.get(k)=='PASS' for k in h.WORLD_KEYS+h.CONTINUATION_KEYS))

    def test_each_missing_or_failed_actual_smoke_gate_blocks_delivery(self):
        for gate in h.PERSONAL_GATES:
            for state in ('FAIL','NOT_RUN',None):
                with self.subTest(gate=gate,state=state),self.assertRaises(ValueError):
                    h.finish_personal(dict(self.proposed,**{gate:state}))

    def test_personal_receipt_cannot_claim_manual_or_full_route_success(self):
        receipt=h.finish_personal(self.proposed)
        for delta in [dict(manual_acceptance='PASS'),dict(runtime='PASS'),dict(quality='STABLE'),
            dict(completedStages=list(h.STAGES)),dict(worldEastPartyAndColdRestart='PASS'),
            dict(stableAcceptance='PASS'),dict(contentHash='f'*64),dict(runtimeScopeSha256='f'*64)]:
            with self.subTest(delta=delta),self.assertRaises(ValueError):
                h.review_personal(dict(receipt,**delta))

    def test_source_scope_cannot_weaken_personal_gates_or_reviewer_jobs(self):
        original=h.read_json
        for delta in ['gates','jobs','quality','manual']:
            scope=copy.deepcopy(self.scope)
            if delta=='gates':scope['personalTest']['gates'].remove('externalColdRestart')
            elif delta=='jobs':scope['personalTest']['requiredJobs']=[]
            elif delta=='quality':scope['quality']='UNKNOWN'
            else:scope['personalTest']['manual_acceptance']='PASS'
            with patch.object(h,'read_json',side_effect=lambda p:scope if p==h.SCOPE_PATH else original(p)),self.assertRaises(ValueError):
                h.active_scope()

    def test_original_shell_short_dispatch_and_failure_propagation(self):
        script=(ROOT/'ci/run-town02-runtime.sh').read_text()
        start=script.index('if [[ "$quality" == PERSONAL_TEST ]]; then')
        block=script[start:script.index('if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test',start)]
        from tests.test_runtime_handoff import existing_bash
        prefix='''set -euo pipefail
quality=PERSONAL_TEST
python(){ printf 'PY %s\\n' "$*"; }
run_test(){ printf 'TEST %s\\n' "$*"; [[ "$1" != "${FAIL_METHOD:-none}" ]]; }
pull_evidence(){ :; }
'''
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'smoke.sh';path.write_text(prefix+block,encoding='utf-8',newline='\n')
            for method,ok in [('none',True),('testUnrestorableSaveCannotBeOverwritten',False)]:
                import os
                result=subprocess.run([existing_bash(),path.as_posix()],env=dict(os.environ,FAIL_METHOD=method),capture_output=True,text=True,timeout=10)
                self.assertEqual(ok,result.returncode==0,result.stderr)
                self.assertNotIn('testNormalNanhaiRouteBossAndVictory',result.stdout)
                if ok:self.assertIn('--cold-test testPersonalR1SmokeColdRestartMatchesVerifiedSave',result.stdout)
                else:self.assertNotIn('PY tools/record_app_audio.py',result.stdout)

    def test_source_bound_approval_quality_matches_scope_and_protections_remain(self):
        workflow=(ROOT/'.github/workflows/android-publish.yml').read_text()
        self.assertIn('RELEASE_QUALITY: '+self.source_scope['quality'],workflow)
        self.assertIn('environment: fengshen-production',workflow)
        self.assertIn('current_user_can_approve == true',workflow)
        self.assertIn('runtime_jobs=(runtime runtime-world runtime-continuation)',workflow)
        checker=(ROOT/'ci/check-reviewed-apk.ps1').read_text()
        self.assertLess(checker.index('signature/content/version failed revalidation'),checker.index("if($quality -eq 'PERSONAL_TEST')"))
        self.assertIn("'base,world,continuation'",checker)
        publisher=(ROOT/'publish-apk.ps1').read_text()
        self.assertIn("$metadata['quality']='STABLE'",publisher)
        self.assertIn("$metadata['stable_acceptance']='PASS'",publisher)
        build=(ROOT/'build-ci.ps1').read_text()
        self.assertIn("if($scopeId -eq 'PLAYABLE-R1')",build)
        self.assertIn('test_world_hell_route_driver.py',build)
        self.assertIn(':app:testReleaseUnitTest',build)

if __name__=='__main__':unittest.main()
