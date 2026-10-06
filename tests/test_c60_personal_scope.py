"""Exact c60 target; missing real save gates or false stable claims cannot publish."""
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


class C60PersonalScopeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.scope = json.loads((ROOT/'ci/golden-world-c60-personal-scope.json').read_text(encoding='utf-8'))
        self.pin = json.loads((ROOT/'ci/golden-world-reference-repair-content.json').read_text(encoding='utf-8'))
        self.scope_path = self.folder/'runtime-scope.json'
        self.scope_path.write_text(json.dumps(self.scope), encoding='utf-8')
        self.pin['runtimeScope'] = dict(path='ci/runtime-scope.json', sha256=h.digest(self.scope_path))
        (self.folder/'content-source.json').write_text(json.dumps(self.pin), encoding='utf-8')
        self.patch = patch.object(h, 'SCOPE_PATH', self.scope_path)
        self.patch.start(); self.addCleanup(self.patch.stop)
        self.candidate = dict(sourceCommit='c'*40, buildRunID='123', sha256='a'*64,
            contentHash=self.pin['manifestSha256'], contentVersion=self.pin['contentVersion'],
            versionCode=84, versionName='fixture-only', signerSha256=self.pin['signerSha256'])

    def test_c60_admitted_only_as_explicit_personal_minimum(self):
        scope = h.active_scope(self.candidate)
        self.assertEqual('WORLD-C60-PERSONAL', scope['id'])
        self.assertEqual(69, len(scope['mapIds']))
        proposed = dict(self.candidate, **{key: 'PASS' for key in h.personal_gates(scope)})
        receipt = h.finish_personal(proposed)
        h.review_personal(receipt)
        self.assertEqual('NOT_RUN', receipt['stableAcceptance'])
        self.assertEqual('PENDING', receipt['manual_acceptance'])
        self.assertTrue(all(receipt[key]=='PASS' for key in h.C60_PERSONAL_GATES))
        self.assertFalse(any(receipt.get(key)=='PASS' for key in h.WORLD_KEYS+h.CONTINUATION_KEYS))

    def test_every_save_gate_is_required_in_creation_and_review(self):
        proposed = dict(self.candidate, **{key: 'PASS' for key in h.personal_gates(self.scope)})
        receipt = h.finish_personal(proposed)
        for key in h.personal_gates(self.scope):
            for value in ('FAIL', 'NOT_RUN', None):
                with self.subTest(gate=key, value=value):
                    with self.assertRaises(ValueError): h.finish_personal(dict(proposed, **{key: value}))
                    with self.assertRaises(ValueError): h.review_personal(dict(receipt, **{key: value}))

    def test_target_dependency_or_quality_cannot_be_substituted(self):
        for field in ('quality', 'manifest', 'map', 'gate', 'endpoint'):
            scope = copy.deepcopy(self.scope)
            if field=='quality': scope['quality']='STABLE'
            elif field=='manifest': scope['manifestSha256']='f'*64
            elif field=='map': scope['mapIds'].remove(148)
            elif field=='gate': scope['personalTest']['gates'].remove('saveHistoryRollback')
            else: scope['endpoint']['mapId']=114
            self.scope_path.write_text(json.dumps(scope), encoding='utf-8')
            pin = copy.deepcopy(self.pin)
            pin['runtimeScope']['sha256']=h.digest(self.scope_path)
            (self.folder/'content-source.json').write_text(json.dumps(pin), encoding='utf-8')
            with self.subTest(field=field), self.assertRaises(ValueError): h.active_scope()

    def test_exact_original_base_exports_and_restores_c60_dependencies(self):
        import sys
        sys.path.insert(0, str(ROOT/'tools'))
        import ci_apk as ci
        import export_development as ex
        base_path = Path(os.environ['FENGSHEN_CONTENT_BASE_APK'])
        base = ci.content(base_path, self.pin['iteration']['base'])
        payload = ex.export_from_base(base, self.pin['iteration']['provenance'], self.pin)
        self.assertEqual(378, len(payload))
        self.assertEqual(self.pin['manifestSha256'], hashlib.sha256(payload['manifest.json']).hexdigest())
        scene = json.loads(payload['scene.json'])
        self.assertEqual(self.scope['mapIds'], sorted(m['id'] for m in scene['maps']))
        ci.validate_item_sources(payload)
        with patch.object(ci, 'CONFIG', self.pin):
            ci.restore(base_path, next_code=84, destination=self.folder/'empty-assets')
        restored = {p.relative_to(self.folder/'empty-assets').as_posix(): p.read_bytes()
                    for p in (self.folder/'empty-assets').rglob('*') if p.is_file()}
        self.assertEqual(payload, restored)

    def test_signed_c60_dispatch_requires_each_actual_save_command(self):
        from tests.test_runtime_handoff import existing_bash
        script = (ROOT/'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
        start = script.index('if [[ "$quality" == PERSONAL_TEST ]]; then')
        block = script[start:script.index('if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test', start)]
        prefix = '''set -euo pipefail
quality=PERSONAL_TEST
scope_id=WORLD-C60-PERSONAL
mkdir -p artifacts/town02-runtime
timeout(){ shift; "$@"; }
adb(){ [[ "${FAIL_AT:-none}" != loader ]] || return 1; printf 'OK (1 test)\\n'; }
run_test(){ printf 'TEST %s\\n' "$*"; [[ "$1" != "${FAIL_AT:-none}" ]]; }
python(){ printf 'PY %s\\n' "$*"; [[ "${2:-receipt}" != "${FAIL_AT:-none}" ]]; }
pull_evidence(){ :; }
'''
        path = self.folder/'signed-smoke.sh'
        path.write_text(prefix+block, encoding='utf-8', newline='\n')
        for failure in ('none', 'loader', 'testControlledSaveHistoryCorruptionAndRetentionProtectActiveAndMigration', 'world-save-history', 'personal-r1-smoke'):
            with self.subTest(failure=failure):
                result = subprocess.run([existing_bash(), path.as_posix()], cwd=self.folder,
                    env=dict(os.environ, FAIL_AT=failure), capture_output=True, text=True, timeout=10)
                self.assertEqual(failure=='none', result.returncode==0, result.stderr)
                if failure=='none':
                    self.assertIn('CorruptionAndRetentionProtectActiveAndMigration false', result.stdout)
                    self.assertIn('--controlled-save-history --cold-test testSaveHistoryExternalColdStartMatchesRestoredSnapshot', result.stdout)
                    self.assertIn('--cold-test testPersonalR1SmokeColdRestartMatchesVerifiedSave', result.stdout)
                else:
                    self.assertNotIn('PY -', result.stdout) # No receipt after any failed command.


if __name__ == '__main__':
    unittest.main()
