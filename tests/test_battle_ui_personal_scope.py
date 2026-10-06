"""Same-candidate UI gates and raw artifact rejection; no App/phone claims."""
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
from tools import runtime_handoff as h, battle_ui_evidence as ui

ROOT = Path(__file__).resolve().parents[1]


class BattleUiPersonalScopeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name); self.scope_path = self.folder / 'runtime-scope.json'
        self.scope = json.loads((ROOT/'ci/golden-world-c61-ui-personal-scope.json').read_text(encoding='utf-8'))
        self.pin = json.loads((ROOT/'ci/golden-world-jiang-content.json').read_text(encoding='utf-8'))
        self.write_scope(self.scope)
        p = patch.object(h, 'SCOPE_PATH', self.scope_path); p.start(); self.addCleanup(p.stop)
        self.candidate = dict(sourceCommit='c'*40, buildRunID='123', sha256='a'*64, versionCode=86,
                              versionName='synthetic-fixture-only', contentVersion=self.pin['contentVersion'],
                              contentHash=self.pin['manifestSha256'], signerSha256=self.pin['signerSha256'])
        self.proposed = dict(self.candidate, **{key:'PASS' for key in h.personal_gates(self.scope)},
                             jiangRecordingSha256='1'*64, jiangColdBoundarySha256='2'*64,
                             **{ui.UI_PROOF_KEY:'3'*64})

    def write_scope(self, value):
        self.scope_path.write_text(json.dumps(value), encoding='utf-8')
        pin = copy.deepcopy(self.pin)
        pin['runtimeScope'] = dict(path='ci/runtime-scope.json', sha256=h.digest(self.scope_path))
        (self.folder/'content-source.json').write_text(json.dumps(pin), encoding='utf-8')

    def test_all_old_and_new_gates_and_proof_digests_are_mandatory(self):
        self.assertEqual(26, len(h.personal_gates(self.scope)))
        self.assertEqual(21, len(h.active_scope()['contentTests']))
        receipt = h.finish_personal(self.proposed); h.review_personal(receipt)
        for key in (*h.C61_PROOF_KEYS, ui.UI_PROOF_KEY): self.assertEqual(self.proposed[key], receipt[key])
        for gate in h.personal_gates(self.scope):
            with self.subTest(gate=gate), self.assertRaises(ValueError):
                h.finish_personal(dict(self.proposed, **{gate:'NOT_RUN'}))
            with self.assertRaises(ValueError): h.review_personal(dict(receipt, **{gate:None}))
        for key in (*h.C61_PROOF_KEYS, ui.UI_PROOF_KEY):
            with self.assertRaises(ValueError): h.finish_personal(dict(self.proposed, **{key:'missing'}))
            with self.assertRaises(ValueError): h.review_personal(dict(receipt, **{key:None}))
        for key in ('audio', 'onePlus13T', 'stableAcceptance', 'manual_acceptance'):
            with self.assertRaises(ValueError): h.review_personal(dict(receipt, **{key:'PASS'}))

    def test_self_consistent_hash_cannot_weaken_ui_fonts_content_or_gates(self):
        for mutation in ('fonts', 'screen', 'safe', 'screenshots', 'logs', 'gate', 'content', 'maps'):
            scope = copy.deepcopy(self.scope)
            if mutation == 'fonts': scope['battleUiAcceptance']['fonts'].pop()
            elif mutation == 'screen': scope['battleUiAcceptance']['screen'] = [960, 540]
            elif mutation == 'safe': scope['battleUiAcceptance']['safe'] = [2640, 1216]
            elif mutation == 'screenshots': scope['battleUiAcceptance']['requiredScreenshotsPerFont'] = 0
            elif mutation == 'logs': scope['battleUiAcceptance']['requiredInstrumentLogs'].pop()
            elif mutation == 'gate': scope['personalTest']['gates'].remove('jiangExternalColdRestart')
            elif mutation == 'content': scope['contentTests'].pop()
            else: scope['mapIds'].remove(142)
            self.write_scope(scope)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): h.active_scope()

    def test_original_review_entry_rechecks_raw_artifacts_not_only_digest_format(self):
        from tests import test_battle_ui_evidence as fixtures
        raw = fixtures.BattleUiEvidenceTest(); raw.setUp(); self.addCleanup(raw.doCleanups)
        receipt = h.finish_personal(dict(self.proposed, **raw.proof()))
        path = raw.logs / 'runtime-receipt.json'; path.write_text(json.dumps(receipt), encoding='utf-8')
        argv = ['runtime_handoff.py', 'review', '--receipt', str(path), '--evidence', str(raw.evidence)]
        with patch.object(sys, 'argv', argv), contextlib.redirect_stdout(io.StringIO()): h.main()
        # Still a valid full-size PNG, but different bytes must reject this receipt.
        from PIL import Image
        Image.new('RGB', (2640, 1216), 'red').save(raw.evidence / ui.screenshot_names('2.0')[-1])
        with patch.object(sys, 'argv', argv), self.assertRaises(ValueError): h.main()
        (raw.evidence / ui.screenshot_names('1.3')[0]).unlink()
        with patch.object(sys, 'argv', argv), self.assertRaises(ValueError): h.main()

    def test_signed_dispatch_failure_never_reaches_personal_receipt(self):
        from tests.test_runtime_handoff import existing_bash
        source = (ROOT/'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
        start = source.index('if [[ "$quality" == PERSONAL_TEST ]]; then')
        block = source[start:source.index('if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test', start)]
        prefix = '''set -euo pipefail
quality=PERSONAL_TEST
scope_id=WORLD-C61-UI-PERSONAL
mkdir -p artifacts/town02-runtime
timeout(){ shift; "$@"; }
adb(){ printf 'OK (1 test)\\n'; }
sleep(){ :; }
run_test(){
    printf 'TEST %s font=%s\\n' "$*" "${font:-none}"
    [[ "$1" != "${FAIL_AT:-none}" ]] || return 1
    [[ "$1" != testControlledBattlePartyPhoneSizeAndLargeFont || "${font:-none}" != "${FAIL_FONT:-none}" ]] || return 1
    printf 'Synthetic dispatch fixture only\\nOK (1 test)\\n' > "artifacts/town02-runtime/$1.txt"
}
python(){ printf 'PY %s\\n' "$*"; [[ "${2:-receipt}" != "${FAIL_AT:-none}" ]]; }
pull_evidence(){ :; }
'''
        path = self.folder / 'ui-smoke.sh'; path.write_text(prefix + block, encoding='utf-8', newline='\n')
        for failure, font in [('none','none'),('world-save-history','none'),('world-jiang','none'),
                              ('testControlledWholly08PartyAdvancesWithoutTouchCommand','none'),
                              ('testControlledMobileBattleTouchAndSnapshots','none'),('none','1.3'),('none','2.0')]:
            result = subprocess.run([existing_bash(), path.as_posix()], cwd=self.folder,
                env=dict(os.environ, FAIL_AT=failure, FAIL_FONT=font), capture_output=True, text=True, timeout=15)
            with self.subTest(failure=failure, font=font):
                success = failure == font == 'none'
                self.assertEqual(success, result.returncode == 0, result.stderr)
                if success:
                    self.assertIn('testControlledBattlePartyPhoneSizeAndLargeFont false font=2.0', result.stdout)
                    self.assertIn('PY -', result.stdout)
                else: self.assertNotIn('PY -', result.stdout)
                if font == '1.3': self.assertNotIn('false font=2.0', result.stdout)
        checker = (ROOT/'ci/check-reviewed-apk.ps1').read_text(encoding='utf-8')
        self.assertIn('--evidence', checker)
        workflow = (ROOT/'.github/workflows/android-build.yml').read_text(encoding='utf-8')
        personal = workflow[workflow.index('- name: Retain the explicitly graded personal smoke receipt'):]
        self.assertIn('artifacts/checkpoint-ui/mobile-*', personal.split('- name: Retain exact normal checkpoint')[0])


if __name__ == '__main__': unittest.main()
