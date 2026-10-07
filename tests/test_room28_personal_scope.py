"""Formal room28 gates preserve old acceptance; fixtures never count as App runs."""
import contextlib
import copy
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools import runtime_handoff as h,room28_evidence as room,battle_ui_evidence as ui

ROOT=Path(__file__).resolve().parents[1]


class Room28PersonalScopeTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.folder=Path(self.temp.name);self.scope_path=self.folder/'runtime-scope.json'
        self.scope=json.loads((ROOT/'ci/golden-town-room28-personal-scope.json').read_text(encoding='utf-8'))
        self.pin=json.loads((ROOT/'ci/golden-town-room28-content.json').read_text(encoding='utf-8'))
        self.write_scope(self.scope)
        p=patch.object(h,'SCOPE_PATH',self.scope_path);p.start();self.addCleanup(p.stop)
        self.candidate=dict(sourceCommit='c'*40,buildRunID='123',sha256='a'*64,versionCode=87,
            versionName='synthetic-fixture-only',contentHash=self.pin['manifestSha256'],
            contentVersion=self.pin['contentVersion'],signerSha256=self.pin['signerSha256'])
        self.proposed=dict(self.candidate,**{k:'PASS' for k in h.personal_gates(self.scope)},
            **{k:'1'*64 for k in (*h.C61_PROOF_KEYS,ui.UI_PROOF_KEY,*room.PROOF_KEYS)})

    def write_scope(self,scope):
        self.scope_path.write_text(json.dumps(scope),encoding='utf-8')
        pin=copy.deepcopy(self.pin)
        pin['runtimeScope']=dict(path='ci/runtime-scope.json',sha256=h.digest(self.scope_path))
        (self.folder/'content-source.json').write_text(json.dumps(pin),encoding='utf-8')

    def test_all_twenty_nine_gates_and_six_raw_proof_digests_required(self):
        scope=h.active_scope(self.candidate)
        self.assertEqual(73,len(scope['mapIds']));self.assertEqual(29,len(h.personal_gates(scope)))
        old=json.loads((ROOT/'ci/golden-world-c61-ui-personal-scope.json').read_text(encoding='utf-8'))
        self.assertEqual(old['personalTest']['gates']+room.GATES,h.personal_gates(scope))
        self.assertEqual(old['contentTests'][1:],scope['contentTests'][1:])
        receipt=h.finish_personal(self.proposed);h.review_personal(receipt)
        for gate in h.personal_gates(scope):
            with self.subTest(gate=gate),self.assertRaises(ValueError):h.finish_personal(dict(self.proposed,**{gate:'NOT_RUN'}))
            with self.assertRaises(ValueError):h.review_personal(dict(receipt,**{gate:None}))
        for key in (*h.C61_PROOF_KEYS,ui.UI_PROOF_KEY,*room.PROOF_KEYS):
            with self.assertRaises(ValueError):h.finish_personal(dict(self.proposed,**{key:'missing'}))
            with self.assertRaises(ValueError):h.review_personal(dict(receipt,**{key:None}))
        for key in ('audio','onePlus13T','stableAcceptance','manual_acceptance'):
            with self.assertRaises(ValueError):h.review_personal(dict(receipt,**{key:'PASS'}))

    def test_publisher_scope_query_needs_only_stdlib_but_raw_png_proof_requires_pillow(self):
        # Reproduce the actual publisher ordering without global/site packages.
        result=subprocess.run([sys.executable,'-S','tools/runtime_handoff.py','scope','--field','quality'],
            cwd=ROOT,capture_output=True,text=True,timeout=15)
        self.assertEqual(0,result.returncode,result.stderr)
        self.assertIn(result.stdout.strip(),('PERSONAL_TEST','STABLE'))
        result=subprocess.run([sys.executable,'-S','-c',
            "from tools.room28_evidence import proof_digests; proof_digests('.')"],
            cwd=ROOT,capture_output=True,text=True,timeout=15)
        self.assertNotEqual(0,result.returncode)
        self.assertIn("No module named 'PIL'",result.stderr)

    def test_self_consistent_scope_hash_cannot_omit_original_guards(self):
        for mutation in ('normalEndpoint','proofKeys','screenshots','font','gate','jiang','content','roomMap','oldMap','fullEndpoint'):
            scope=copy.deepcopy(self.scope)
            if mutation=='normalEndpoint':scope['normalRoom28Acceptance']['returnCell']=[12,24]
            elif mutation=='proofKeys':scope['normalRoom28Acceptance']['proofKeys'].pop()
            elif mutation=='screenshots':scope['normalRoom28Acceptance']['requiredScreenshots']=0
            elif mutation=='font':scope['battleUiAcceptance']['fonts'].pop()
            elif mutation=='gate':scope['personalTest']['gates'].remove('room28ExternalColdAndReentry')
            elif mutation=='jiang':scope['controlledEndpoint']['party'].pop()
            elif mutation=='content':scope['contentTests'].pop()
            elif mutation=='roomMap':scope['mapIds'].remove(28)
            elif mutation=='oldMap':scope['mapIds'].remove(142)
            else:scope['endpoint']['bossFlag']='invented'
            self.write_scope(scope)
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):h.active_scope()

    def test_original_review_recomputes_room_bytes(self):
        from tests.test_room28_evidence import Room28EvidenceTest
        raw=Room28EvidenceTest();raw.setUp();self.addCleanup(raw.doCleanups)
        proofs=room.proof_digests(raw.folder)
        receipt=h.finish_personal(dict(self.proposed,**proofs))
        path=self.folder/'runtime-receipt.json';path.write_text(json.dumps(receipt),encoding='utf-8')
        argv=['runtime_handoff.py','review','--receipt',str(path),'--evidence',str(raw.folder)]
        # Other raw gates are independently covered by their existing artifact tests.
        with patch.object(ui,'proof_digests',return_value={ui.UI_PROOF_KEY:receipt[ui.UI_PROOF_KEY]}), \
                patch.object(h,'jiang_proof_digests',return_value={k:receipt[k] for k in h.C61_PROOF_KEYS}), \
                patch.object(sys,'argv',argv):
            with contextlib.redirect_stdout(io.StringIO()):h.main()
            changed=raw.folder/'touch-ux-world-room28-normal-index.json'
            value=json.loads(changed.read_text(encoding='utf-8'));value['additionalMetadata']='changed bytes'
            changed.write_text(json.dumps(value),encoding='utf-8')
            with self.assertRaises(ValueError):h.main()

    def test_original_signed_dispatch_stops_before_receipt_for_room_and_old_failures(self):
        from tests.test_runtime_handoff import existing_bash
        source=(ROOT/'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
        start=source.index('if [[ "$quality" == PERSONAL_TEST ]]; then')
        block=source[start:source.index('if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test',start)]
        prefix='''set -euo pipefail
quality=PERSONAL_TEST
scope_id=WORLD-C62-ROOM-PERSONAL
mkdir -p artifacts/town02-runtime
timeout(){ shift; "$@"; }
adb(){ printf 'OK (1 test)\\n'; }
sleep(){ :; }
run_test(){ printf 'TEST %s\\n' "$*"; [[ "$1" != "${FAIL_AT:-none}" ]]; printf 'OK (1 test)\\n' > "artifacts/town02-runtime/$1.txt"; }
python(){ printf 'PY %s\\n' "$*"; [[ "${2:-receipt}" != "${FAIL_AT:-none}" ]]; }
pull_evidence(){ :; }
'''
        script=self.folder/'signed-room28.sh';script.write_text(prefix+block,encoding='utf-8',newline='\n')
        for failure in ('none','world-room28','world-jiang','world-save-history','personal-r1-smoke','testControlledR1ReplayVersionMarkerBounds','testControlledBattlePartyPhoneSizeAndLargeFont'):
            result=subprocess.run([existing_bash(),script.as_posix()],cwd=self.folder,
                env=dict(os.environ,FAIL_AT=failure),capture_output=True,text=True,timeout=15)
            with self.subTest(failure=failure):
                self.assertEqual(failure=='none',result.returncode==0,result.stderr)
                if failure=='none':self.assertIn('world-room28 testNormalTownRoom28EntryInvestigationAndSave',result.stdout)
                else:self.assertNotIn('PY -',result.stdout)
        workflow=(ROOT/'.github/workflows/android-build.yml').read_text(encoding='utf-8')
        personal=workflow[workflow.index('- name: Retain the explicitly graded personal smoke receipt'):].split('- name: Retain exact normal checkpoint')[0]
        for glob in ('world-room28-*','touch-ux-world-room28-*','c62-room28-*.txt'):self.assertIn(glob,personal)


if __name__=='__main__':unittest.main()
