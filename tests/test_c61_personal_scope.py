"""The signed Jiang tier requires all old gates plus actual cold/video evidence."""
import copy
import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from tools import runtime_handoff as h

ROOT = Path(__file__).resolve().parents[1]


class C61PersonalScopeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.scope = json.loads((ROOT/'ci/golden-world-c61-personal-scope.json').read_text(encoding='utf-8'))
        self.pin = json.loads((ROOT/'ci/golden-world-jiang-content.json').read_text(encoding='utf-8'))
        self.scope_path = self.folder/'runtime-scope.json'
        self.write_scope(self.scope)
        p = patch.object(h, 'SCOPE_PATH', self.scope_path)
        p.start(); self.addCleanup(p.stop)
        self.candidate = dict(sourceCommit='c'*40, buildRunID='123', sha256='a'*64,
            contentHash=self.pin['manifestSha256'], contentVersion=self.pin['contentVersion'],
            versionCode=85, versionName='fixture-only', signerSha256=self.pin['signerSha256'])
        self.proposed = dict(self.candidate, **{key:'PASS' for key in h.personal_gates(self.scope)},
            jiangRecordingSha256='1'*64, jiangColdBoundarySha256='2'*64)

    def write_scope(self, scope):
        self.scope_path.write_text(json.dumps(scope), encoding='utf-8')
        pin = copy.deepcopy(self.pin)
        pin['runtimeScope'] = dict(path='ci/runtime-scope.json', sha256=h.digest(self.scope_path))
        (self.folder/'content-source.json').write_text(json.dumps(pin), encoding='utf-8')

    def test_all_eighteen_gates_and_two_evidence_digests_survive_review(self):
        self.assertEqual(72, len(h.active_scope(self.candidate)['mapIds']))
        self.assertEqual(18, len(h.personal_gates(self.scope)))
        receipt = h.finish_personal(self.proposed)
        self.assertEqual('PENDING', receipt['manual_acceptance'])
        self.assertEqual('NOT_RUN', receipt['stableAcceptance'])
        for key in h.C61_PROOF_KEYS: self.assertEqual(self.proposed[key], receipt[key])
        h.review_personal(receipt)
        for gate in h.personal_gates(self.scope):
            for value in (None, 'FAIL', 'NOT_RUN'):
                with self.subTest(gate=gate, value=value), self.assertRaises(ValueError):
                    h.finish_personal(dict(self.proposed, **{gate:value}))
                with self.subTest(review_gate=gate, value=value), self.assertRaises(ValueError):
                    h.review_personal(dict(receipt, **{gate:value}))
        for key in h.C61_PROOF_KEYS:
            for value in (None, '', 'f'*63, 'g'*64):
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    h.finish_personal(dict(self.proposed, **{key:value}))
                with self.assertRaises(ValueError): h.review_personal(dict(receipt, **{key:value}))

    def test_self_consistent_changed_scope_still_rejects_weakened_or_swapped_target(self):
        for field in ('quality', 'manifest', 'maps', 'gate', 'endpoint', 'controlledEndpoint', 'contentTests'):
            scope = copy.deepcopy(self.scope)
            if field == 'quality': scope['quality'] = 'STABLE'
            elif field == 'manifest': scope['manifestSha256'] = 'f'*64
            elif field == 'maps': scope['mapIds'].remove(142)
            elif field == 'gate': scope['personalTest']['gates'].remove('jiangFourPartyBattle')
            elif field == 'endpoint': scope['endpoint']['mapId'] = 7
            elif field == 'controlledEndpoint': scope['controlledEndpoint']['party'].pop()
            else: scope['contentTests'].remove('testC61FrozenDependenciesAndMedicalPartySave')
            self.write_scope(scope) # recompute transport hash: semantic guards still apply.
            with self.subTest(field=field), self.assertRaises(ValueError): h.active_scope()

    def recording_fixture(self):
        before = dict(contentVersion='opening-segment-001-c61', mapId=7,
            characters=[dict(id=n) for n in ('nezha','xiaolongnv','yangjian','jiangziya')],
            flags={'rom.event.7.21.dialogue.pending':True})
        names = ['world-jiang-normal-00.mp4','world-jiang-cold-restart.mp4']
        segments = []
        for name in names:
            data = ('isolated-test-not-real-video:'+name).encode()
            (self.folder/name).write_bytes(data)
            segments.append(dict(file='artifacts/checkpoint-ui/'+name,sha256=hashlib.sha256(data).hexdigest()))
        segments[0]['savedWorldAfter'] = before
        segments[1]['savedWorldBefore'] = before
        proof = dict(kind='CONTROLLED_JIANG_INVITATION_SMOKE', controlledAssertions='PASS',
            normalAssertions='NOT_APPLICABLE', forceStopRestartEqual=True,continuedExploration=False,
            originalPreferencesRestored=True, segments=segments,
            videos=['artifacts/checkpoint-ui/'+n for n in names])
        boundary = dict(kind='ACTUAL_APP_EXTERNAL_COLD_BOUNDARY', before=before,after=copy.deepcopy(before),
            equal=True,differentTopLevelFields=[])
        return proof,boundary

    def write_proof(self, proof, boundary):
        (self.folder/'world-jiang-recording.json').write_text(json.dumps(proof),encoding='utf-8')
        (self.folder/'world-jiang-cold-boundary.json').write_text(json.dumps(boundary),encoding='utf-8')

    def test_false_cold_success_missing_video_or_changed_bytes_cannot_satisfy_gates(self):
        proof,boundary = self.recording_fixture()
        self.write_proof(proof,boundary)
        self.assertEqual(set(h.C61_PROOF_KEYS),set(h.jiang_proof_digests(self.folder)))
        for mutation in ('coldFalse','differentState','differentField','missingBefore','wrongVersion',
                'preferences','normalClaim','missingSegment','staleRecorderState','wrongDigest','wrongPath'):
            p,b = copy.deepcopy((proof,boundary))
            if mutation == 'coldFalse': b['equal'] = False
            elif mutation == 'differentState': b['after']['flags']['extra'] = False
            elif mutation == 'differentField': b['differentTopLevelFields'] = ['flags']
            elif mutation == 'missingBefore': del b['before']
            elif mutation == 'wrongVersion': b['before']['contentVersion'] = b['after']['contentVersion'] = 'opening-segment-001-c60'
            elif mutation == 'preferences': p['originalPreferencesRestored'] = False
            elif mutation == 'normalClaim': p['normalAssertions'] = 'PASS'
            elif mutation == 'missingSegment': p['segments'].pop()
            elif mutation == 'staleRecorderState': p['segments'][1]['savedWorldBefore']['money'] = 1
            elif mutation == 'wrongDigest': p['segments'][0]['sha256'] = 'a'*64
            else: p['segments'][0]['file'] = '../untrusted.mp4'
            self.write_proof(p,b)
            with self.subTest(mutation=mutation),self.assertRaises(ValueError): h.jiang_proof_digests(self.folder)
        self.write_proof(proof,boundary)
        (self.folder/'world-jiang-cold-restart.mp4').unlink()
        with self.assertRaises(ValueError): h.jiang_proof_digests(self.folder)

    def test_original_signed_shell_stops_before_receipt_on_any_jiang_failure(self):
        from tests.test_runtime_handoff import existing_bash
        script = (ROOT/'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
        start = script.index('if [[ "$quality" == PERSONAL_TEST ]]; then')
        block = script[start:script.index('if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test',start)]
        prefix = '''set -euo pipefail
quality=PERSONAL_TEST
scope_id=WORLD-C61-PERSONAL
mkdir -p artifacts/town02-runtime
timeout(){ shift; "$@"; }
adb(){ [[ "${FAIL_AT:-none}" != loader ]] || return 1; printf 'OK (1 test)\\n'; }
run_test(){ printf 'TEST %s\\n' "$*"; [[ "$1" != "${FAIL_AT:-none}" ]]; }
python(){ printf 'PY %s\\n' "$*"; [[ "${2:-receipt}" != "${FAIL_AT:-none}" ]]; }
pull_evidence(){ :; }
'''
        path = self.folder/'signed-smoke.sh'
        path.write_text(prefix+block,encoding='utf-8',newline='\n')
        for failure in ('none','loader','world-jiang','world-save-history',
                        'testControlledR1ReplayVersionMarkerBounds','personal-r1-smoke'):
            result = subprocess.run([existing_bash(),path.as_posix()],cwd=self.folder,
                env=dict(os.environ,FAIL_AT=failure),capture_output=True,text=True,timeout=10)
            with self.subTest(failure=failure):
                self.assertEqual(failure=='none',result.returncode==0,result.stderr)
                if failure=='none':
                    self.assertIn('--controlled-jiang --cold-test testJiangExternalColdStartMatchesPendingAndCompletesOnce',result.stdout)
                else: self.assertNotIn('PY -',result.stdout)


if __name__ == '__main__': unittest.main()
