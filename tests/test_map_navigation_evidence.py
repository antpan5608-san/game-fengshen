"""Synthetic rejection/dispatch fixtures only; these are not Android acceptance."""
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
from PIL import Image
from tools import map_navigation_evidence as nav, runtime_handoff as h

ROOT = Path(__file__).resolve().parents[1]


class MapNavigationEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name); self.raw = self.root / 'raw'; self.logs = self.root / 'logs'
        self.raw.mkdir(); self.logs.mkdir()
        self.candidate = dict(sourceCommit='c' * 40, buildRunID='123', sha256='a' * 64,
            contentHash='b' * 64, contentVersion='opening-segment-001-c62', versionCode=102,
            versionName='SYNTHETIC_NOT_APP', signerSha256='d' * 64)
        for font in nav.FONTS:
            value = dict(model=nav.MODEL, scope=nav.KIND, versionCode=102,
                contentVersion=self.candidate['contentVersion'], mapId=114, fontScale=float(font),
                frameWidth=2640, frameHeight=1216, cyanPixels=100,
                groundExpectedSteps=6, groundActualSteps=6, objectExpectedSteps=2, objectActualSteps=2,
                objectId='original.test-npc', arrivalFace='LEFT', **{k: True for k in nav.BOOLEAN_CHECKS})
            self.write(font, value)
            (self.logs / f'{nav.METHOD}-font-{font}.txt').write_text('OK (1 test)\n', encoding='utf-8')
            image = Image.new('RGB', (2640, 1216), 'black')
            image.paste((41, 223, 255), (10, 10, 20, 20))
            image.save(self.raw / f'touch-ux-world01-navigation-ground-{font}.png')
            image.paste('white', (100, 100, 300, 300))
            image.save(self.raw / f'touch-ux-world01-navigation-object-options-{font}.png')

    def write(self, font, value):
        (self.raw / f'touch-ux-world01-navigation-touch-{font}.json').write_text(json.dumps(value), encoding='utf-8')

    def value(self):
        return json.loads((self.raw / 'touch-ux-world01-navigation-touch-1.0.json').read_text())

    def proof(self, candidate=None):
        return nav.proof_digests(self.raw, self.logs, candidate or self.candidate)

    def test_complete_bytes_bind_exact_candidate_identity(self):
        proof = self.proof(); nav.validate_digests(proof)
        for k, v in [('sourceCommit', 'e' * 40), ('sha256', 'f' * 64), ('buildRunID', '456')]:
            self.assertNotEqual(proof, self.proof(dict(self.candidate, **{k: v})))
        with self.assertRaises(ValueError): self.proof(dict(self.candidate, versionCode=101))
        with self.assertRaises(ValueError): self.proof(dict(self.candidate, versionCode=103))

    def test_every_font_json_png_and_instrument_log_is_mandatory(self):
        for directory, path in [(self.raw, n) for n in os.listdir(self.raw)] + [(self.logs, n) for n in os.listdir(self.logs)]:
            p = directory / path; data = p.read_bytes(); p.unlink()
            with self.subTest(path=path), self.assertRaises(ValueError): self.proof()
            p.write_bytes(data)

    def test_old_protocol_version_font_and_false_or_numeric_assertions_reject(self):
        original = self.value()
        changes = [('model', 'OLD'), ('scope', 'NORMAL_WORLD_COMPLETE'), ('versionCode', 101),
                   ('versionCode', True), ('contentVersion', 'old'), ('mapId', True), ('mapId', 0),
                   ('fontScale', 2), ('fontScale', True), ('fontScale', float('nan'))]
        changes += [(k, v) for k in nav.BOOLEAN_CHECKS for v in (None, False, 1)]
        for key, value in changes:
            self.write('1.0', dict(original, **{key: value}))
            with self.subTest(key=key, value=value), self.assertRaises(ValueError): self.proof()

    def test_steps_original_facing_identity_and_geometry_must_be_actual(self):
        original = self.value()
        for key, value in [('groundActualSteps', 7), ('groundExpectedSteps', True),
                           ('objectActualSteps', 3), ('objectExpectedSteps', 0), ('objectId', '../bad'),
                           ('objectId', ''), ('arrivalFace', 'A'), ('frameWidth', True),
                           ('frameHeight', 540), ('cyanPixels', 20), ('cyanPixels', True)]:
            self.write('1.0', dict(original, **{key: value}))
            with self.subTest(key=key), self.assertRaises(ValueError): self.proof()

    def test_changed_actual_cyan_pixels_and_blank_or_wrong_size_png_reject(self):
        path = self.raw / 'touch-ux-world01-navigation-ground-1.0.png'
        for size, color in [((2640, 1216), 'red'), ((960, 540), 'black')]:
            Image.new('RGB', size, color).save(path)
            with self.assertRaises(ValueError): self.proof()
        image = Image.new('RGB', (2640, 1216), 'black'); image.paste((41, 223, 255), (0, 0, 11, 10)); image.save(path)
        with self.assertRaisesRegex(ValueError, 'cyan PNG'): self.proof()

    def test_failure_marker_cannot_be_hidden_by_ok_log(self):
        path = self.logs / f'{nav.METHOD}-font-1.0.txt'
        for marker in ['FAILURES!!!', 'INSTRUMENTATION_ABORTED', 'INSTRUMENTATION_FAILED', 'Process crashed']:
            path.write_text('OK (1 test)\n' + marker, encoding='utf-8')
            with self.assertRaises(ValueError): self.proof()

    def test_unsafe_or_oversized_raw_and_non_object_json_reject(self):
        with patch.object(Path, 'is_symlink', return_value=True), self.assertRaises(ValueError): self.proof()
        self.write('1.0', []);
        with self.assertRaises(ValueError): self.proof()
        (self.raw / 'touch-ux-world01-navigation-touch-1.0.json').write_bytes(b' ' * (65536 + 1))
        with self.assertRaises(ValueError): self.proof()

    def install_scope(self):
        self.scope = json.loads((ROOT / 'ci/runtime-scope.json').read_text())
        self.pin = json.loads((ROOT / 'ci/content-source.json').read_text())
        self.scope_path = self.root / 'runtime-scope.json'
        self.write_scope(self.scope)
        p = patch.object(h, 'SCOPE_PATH', self.scope_path); p.start(); self.addCleanup(p.stop)
        self.candidate['contentHash'] = self.pin['manifestSha256']
        proposed = dict(self.candidate, **{k: 'PASS' for k in h.personal_gates(self.scope)})
        for keys in [h.C61_PROOF_KEYS, (h.battle_ui.UI_PROOF_KEY,), h.room28.PROOF_KEYS,
                     h.field_magic.PROOF_KEYS, h.battle_magic.PROOF_KEYS, h.visual.PROOF_KEYS, nav.PROOF_KEYS]:
            proposed.update({k: '1' * 64 for k in keys})
        return proposed

    def write_scope(self, scope):
        self.scope_path.write_text(json.dumps(scope), encoding='utf-8')
        pin = copy.deepcopy(self.pin); pin['runtimeScope']['sha256'] = h.digest(self.scope_path)
        (self.root / 'content-source.json').write_text(json.dumps(pin), encoding='utf-8')

    def test_original_personal_receipt_preserves_old_gates_and_requires_navigation(self):
        proposed = self.install_scope(); scope = h.active_scope(self.candidate)
        self.assertEqual(37, len(h.personal_gates(scope))); self.assertEqual(21, len(scope['contentTests']))
        receipt = h.finish_personal(proposed); h.review_personal(receipt)
        for key in nav.GATES + list(nav.PROOF_KEYS):
            bad = dict(proposed); bad.pop(key)
            with self.subTest(key=key), self.assertRaises(ValueError): h.finish_personal(bad)
            bad = dict(receipt); bad.pop(key)
            with self.assertRaises(ValueError): h.review_personal(bad)
        for key in ['audio', 'onePlus13T', 'stableAcceptance', 'manual_acceptance']:
            with self.assertRaises(ValueError): h.review_personal(dict(receipt, **{key: 'PASS'}))

    def test_even_rehashed_scope_cannot_omit_new_protocol_font_method_or_gate(self):
        self.install_scope()
        for mutation in ['remove', 'font', 'method', 'kind', 'gate']:
            scope = copy.deepcopy(self.scope)
            if mutation == 'remove':
                scope.pop('mapNavigationAcceptance'); scope['personalTest']['gates'].remove(nav.GATES[0])
            elif mutation == 'font': scope['mapNavigationAcceptance']['fonts'].pop()
            elif mutation == 'method': scope['mapNavigationAcceptance']['method'] = 'old'
            elif mutation == 'kind': scope['mapNavigationAcceptance']['kind'] = 'NORMAL_COMPLETE'
            else: scope['personalTest']['gates'].remove(nav.GATES[0])
            self.write_scope(scope)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): h.active_scope(self.candidate)
        # An explicitly historical pre-navigation source retains its original gates.
        scope = copy.deepcopy(self.scope); scope.pop('mapNavigationAcceptance')
        scope['personalTest']['gates'].remove(nav.GATES[0]); self.write_scope(scope)
        self.assertEqual(36, len(h.personal_gates(h.active_scope(dict(self.candidate, versionCode=101)))))

    def test_original_review_rechecks_navigation_bytes_instead_of_digest_shape(self):
        proposed = self.install_scope(); proposed.update(self.proof())
        receipt = h.finish_personal(proposed); path = self.logs / 'runtime-receipt.json'
        path.write_text(json.dumps(receipt), encoding='utf-8')
        argv = ['runtime_handoff.py', 'review', '--receipt', str(path), '--evidence', str(self.raw)]
        with contextlib.ExitStack() as stack:
            stack.enter_context(patch.object(sys, 'argv', argv))
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            for module in [h.battle_ui, h.room28, h.field_magic, h.battle_magic, h.visual]:
                keys = (module.UI_PROOF_KEY,) if module is h.battle_ui else module.PROOF_KEYS
                stack.enter_context(patch.object(module, 'proof_digests', return_value={k: receipt[k] for k in keys}))
            stack.enter_context(patch.object(h, 'jiang_proof_digests', return_value={k: receipt[k] for k in h.C61_PROOF_KEYS}))
            h.main()
            value = self.value(); value['extraSyntheticMetadata'] = 'changed bytes'; self.write('1.0', value)
            with self.assertRaises(ValueError): h.main()

    def test_original_signed_dispatch_stops_before_receipt_when_navigation_font_fails(self):
        from tests.test_runtime_handoff import existing_bash
        source = (ROOT / 'ci/run-town02-runtime.sh').read_text()
        start = source.index('if [[ "$quality" == PERSONAL_TEST ]]; then')
        block = source[start:source.index('if [[ "$scope_id" == PLAYABLE-R1 ]]; then run_test', start)]
        prefix = '''set -euo pipefail
quality=PERSONAL_TEST
scope_id=WORLD-C62-ROOM-PERSONAL
mkdir -p artifacts/town02-runtime
timeout(){ shift; "$@"; }
adb(){ printf 'OK (1 test)\\n'; }
sleep(){ :; }
run_test(){ printf 'TEST %s FONT %s\\n' "$*" "${font:-none}"; [[ "$1:${font:-none}" != "testControlledNavigationHiddenGroundAndObjectCancel:${FAIL_FONT:-none}" ]]; printf 'OK (1 test)\\n' > "artifacts/town02-runtime/$1.txt"; }
pull_evidence(){ :; }
python(){ printf 'PY %s\\n' "$*"; }
'''
        path = self.root / 'dispatch.sh'; path.write_text(prefix + block, encoding='utf-8', newline='\n')
        for failure in ['none', '1.0', '1.3', '2.0']:
            result = subprocess.run([existing_bash(), path.as_posix()], cwd=self.root,
                env=dict(os.environ, FAIL_FONT=failure), capture_output=True, text=True, timeout=10)
            with self.subTest(failure=failure):
                self.assertEqual(failure == 'none', result.returncode == 0, result.stderr)
                if failure == 'none':
                    for font in nav.FONTS:
                        self.assertIn(f'TEST {nav.METHOD} false FONT {font}', result.stdout)
                    self.assertIn('PY -', result.stdout)
                else: self.assertNotIn('PY -\n', result.stdout)

    def test_publisher_selected_personal_artifact_retains_navigation_raw(self):
        # Validate the selected artifact's path glob against every exact raw name,
        # not another artifact that the original publisher never downloads.
        import fnmatch
        source = (ROOT / '.github/workflows/android-build.yml').read_text()
        start = source.index('name: fengshen-town02-runtime-evidence\n')
        block = source[start:source.index('if-no-files-found:', start)]
        patterns = [line.strip() for line in block.splitlines() if line.strip().startswith('artifacts/')]
        for font in nav.FONTS:
            for suffix in [f'touch-{font}.json', f'ground-{font}.png', f'object-options-{font}.png']:
                name = 'artifacts/checkpoint-ui/touch-ux-world01-navigation-' + suffix
                self.assertTrue(any(fnmatch.fnmatchcase(name, pattern) for pattern in patterns), name)

    def test_actual_collector_only_pulls_explicit_navigation_evidence(self):
        source = (ROOT / 'ci/run-town02-runtime.sh').read_text()
        start = source.index("python - <<'PYEVIDENCE'\n") + len("python - <<'PYEVIDENCE'\n")
        block = source[start:source.index('\nPYEVIDENCE', start)]
        names = [f'world01-navigation-{stage}-{font}.{extension}' for font in nav.FONTS
                 for stage, extension in [('touch', 'json'), ('ground', 'png'), ('object-options', 'png')]]
        calls = []
        def adb(arguments, **kwargs):
            if arguments[:3] == ['adb', 'shell', 'ls']:
                return subprocess.CompletedProcess(arguments, 0,
                    stdout='\n'.join(names + ['player-save.json', 'world01-navigation-secret.json',
                                             'world01-navigation-touch-9.0.json']), stderr='')
            calls.append(arguments)
            return subprocess.CompletedProcess(arguments, 0)
        previous = Path.cwd()
        try:
            os.chdir(self.root)
            with patch.object(subprocess, 'run', side_effect=adb):
                exec(compile(block, 'original-PYEVIDENCE', 'exec'), {})
        finally: os.chdir(previous)
        self.assertEqual(names, [Path(args[2]).name for args in calls])
        self.assertEqual(['artifacts/checkpoint-ui/touch-ux-' + name for name in names], [args[3] for args in calls])
