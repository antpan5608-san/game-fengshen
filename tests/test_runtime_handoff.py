"""Isolated transport/receipt rejection tests; these are not Android gameplay."""
import copy
import json
import os
import shutil
import tempfile
import unittest
import subprocess
from pathlib import Path
from unittest.mock import patch
from tools import runtime_handoff as handoff


def existing_bash(platform=None):
    """Use the runner's Git Bash on Windows, never its WSL launcher shim."""
    if (platform or os.name) != 'nt':
        selected = shutil.which('bash')
        if not selected:
            raise RuntimeError('Existing Bash is required for runtime dispatch validation')
        return selected
    git = shutil.which('git')
    candidates = [Path(git).resolve().parent.parent / 'bin/bash.exe'] if git else []
    candidates += [Path(os.environ.get('ProgramFiles', 'C:/Program Files')) / 'Git/bin/bash.exe']
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    raise RuntimeError('Existing Git Bash required; do not substitute the WSL launcher')


class RuntimeHandoffTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.candidate = dict(sourceCommit='c' * 40, buildRunID='123', sha256='a' * 64,
            contentHash='b' * 64, contentVersion='test-content', versionCode=1,
            versionName='test-only', signerSha256='d' * 64)
        self.proposed = dict(self.candidate, runtime='PASS', upgrade='PASS',
            normalHerbSupply='PASS', worldWholly08Controller='PASS', worldMedicalControlledCommands='PASS')
        self.proposed.update({key: 'PASS' for key in handoff.WORLD_KEYS + handoff.CONTINUATION_KEYS})
        self.expected = dict(version=1, contentVersion='test-content', inventory={'test-herb': 3},
                             characters=[{'id': 'test-actor', 'hp': 20}])

    def json(self, path, data):
        path.write_text(json.dumps(data), encoding='utf-8')

    def make_bundle(self, stage='base'):
        source = self.root / 'source'
        source.mkdir()
        previous = handoff.finish_stage('base', self.proposed)
        if stage == 'world':
            previous = handoff.finish_stage('world', self.proposed, previous)
        receipt = self.root / 'runtime-receipt.json'
        self.json(receipt, previous)
        for label in handoff.CHECKPOINTS[stage]:
            self.json(source / f'world-{label}-expected-save.json', self.expected)
            self.json(source / f'world-{label}-normal-index.json', dict(kind='CONTINUATION_FROM_VERIFIED_SAVE',
                stateChangesAtLoad=False, events=[dict(snapshot=self.expected)]))
            self.json(source / f'world-{label}-recording.json', dict(normalAssertions='PASS',
                forceStopRestartEqual=True, continuedExploration=True, segments=[dict(
                    phase='EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE', savedWorldBefore=self.expected)]))
        output = self.root / 'bundle'
        handoff.pack(stage, source, receipt, output, self.candidate)
        return source, output

    def test_stage_receipts_do_not_claim_unexecuted_gates(self):
        base = handoff.finish_stage('base', self.proposed)
        self.assertEqual('PARTIAL', base['runtime'])
        self.assertNotIn('worldQueenRouteAndBindingNormal', base)
        self.assertNotIn('worldCave85Normal', base)
        world = handoff.finish_stage('world', self.proposed, base)
        self.assertEqual('PARTIAL', world['runtime'])
        self.assertNotIn('worldQueenRouteAndBindingNormal', world)
        self.assertEqual('PASS', world['worldCave85Normal'])
        final = handoff.finish_stage('continuation', self.proposed, world)
        self.assertEqual('PASS', final['runtime'])
        self.assertEqual(list(handoff.STAGES), final['completedStages'])
        self.assertEqual('PASS', final['upgrade'])
        self.assertEqual('PASS', final['worldQueenRouteAndBindingNormal'])

    def test_stage_cannot_skip_or_substitute_a_candidate(self):
        base = handoff.finish_stage('base', self.proposed)
        for stage, previous in [('world', None), ('continuation', base),
                                ('world', dict(base, sha256='e' * 64)), ('world', dict(base, runtime='PASS'))]:
            with self.subTest(stage=stage, previous=previous):
                with self.assertRaises(ValueError):
                    handoff.finish_stage(stage, self.proposed, previous)

    def test_exact_bytes_can_pass_both_handoff_stages(self):
        source, output = self.make_bundle('world')
        receipt = handoff.verify(output, 'continuation', self.candidate)
        self.assertEqual(['base', 'world'], receipt['completedStages'])
        for label in handoff.CHECKPOINTS['world']:
            name = f'world-{label}-expected-save.json'
            self.assertEqual((source / name).read_bytes(), (output / name).read_bytes())

    def test_wrong_source_run_apk_content_signature_or_version_rejected(self):
        _, output = self.make_bundle()
        for key in handoff.BINDING_KEYS:
            other = dict(self.candidate)
            other[key] = 9 if key == 'versionCode' else 'wrong'
            with self.subTest(key=key), self.assertRaises(ValueError):
                handoff.verify(output, 'world', other)

    def test_mutated_state_or_added_file_rejected_before_avd_access(self):
        _, output = self.make_bundle()
        save = output / 'world-north-palace-expected-save.json'
        self.json(save, dict(self.expected, inventory={'test-herb': 999}))
        with patch.object(handoff.subprocess, 'check_output') as adb:
            with self.assertRaises(ValueError):
                handoff.import_to_isolated_avd(output, 'world', self.candidate, self.root / 'previous.json')
            adb.assert_not_called()
        save.write_bytes((self.root / 'source' / save.name).read_bytes())
        self.json(output / 'untrusted-extra.json', {'not': 'allowlisted'})
        with self.assertRaises(ValueError):
            handoff.verify(output, 'world', self.candidate)

    def test_forged_manifest_does_not_override_normal_or_cold_boundary(self):
        _, output = self.make_bundle()
        record = output / 'world-north-palace-recording.json'
        changed = handoff.read_json(record)
        changed['segments'][0]['savedWorldBefore']['characters'][0]['hp'] = 999
        self.json(record, changed)
        manifest = output / 'handoff.json'
        data = handoff.read_json(manifest)
        data['files'][record.name] = handoff.digest(record)
        self.json(manifest, data)
        with self.assertRaisesRegex(ValueError, 'cold-start boundary'):
            handoff.verify(output, 'world', self.candidate)

    def test_no_phone_access_and_no_real_preferences_written(self):
        _, output = self.make_bundle()
        with patch.object(handoff.subprocess, 'check_output', return_value=b'real-phone'), \
                patch.object(handoff.subprocess, 'run') as adb:
            with self.assertRaisesRegex(ValueError, 'isolated AOSP'):
                handoff.import_to_isolated_avd(output, 'world', self.candidate, self.root / 'previous.json')
            adb.assert_not_called()

    def test_isolated_import_pushes_only_byte_exact_normal_json(self):
        _, output = self.make_bundle()
        save = output / 'world-north-palace-expected-save.json'
        sha = handoff.digest(save)
        with patch.object(handoff.subprocess, 'check_output', side_effect=[b'ranchu', (sha + ' file').encode()]), \
                patch.object(handoff.subprocess, 'run') as adb:
            receipt = self.root / 'previous.json'
            handoff.import_to_isolated_avd(output, 'world', self.candidate, receipt)
            calls = [call.args[0] for call in adb.call_args_list]
            pushes = [call for call in calls if 'push' in call]
            self.assertEqual(1, len(pushes))
            self.assertEqual(str(save), pushes[0][-2])
            self.assertEqual('/sdcard/Android/data/org.fengshen.dev/files/' + save.name, pushes[0][-1])
            self.assertTrue(all(call[:3] == ['adb', '-s', 'emulator-5554'] for call in calls))
            self.assertFalse(any('shared_prefs' in str(call) or 'clear' in call for call in calls))
            self.assertEqual(['base'], handoff.read_json(receipt)['completedStages'])

    def test_failed_cold_restart_cannot_create_handoff(self):
        source, _ = self.make_bundle()
        path = source / 'world-north-palace-recording.json'
        data = handoff.read_json(path)
        data['forceStopRestartEqual'] = False
        self.json(path, data)
        with self.assertRaises(ValueError):
            handoff.pack('base', source, self.root / 'runtime-receipt.json',
                         self.root / 'failed-bundle', self.candidate)
        self.assertFalse((self.root / 'failed-bundle').exists())

    def test_actual_shell_stage_dispatch_keeps_every_normal_and_cold_test(self):
        # Execute the actual dispatch block against harmless shell functions;
        # no SDK/AVD/GitHub or App state is accessed by this transport test.
        root = Path(__file__).resolve().parents[1]
        script = (root / 'ci/run-town02-runtime.sh').read_text()
        start = script.index('if [[ "$stage" == all || "$stage" == base ]]; then\nrun_test testUpgradeKeepsPreviousSave')
        block = script[start:script.index('# Clinical sub-results', start)]
        prefix = '''set -euo pipefail
stage="$1"
mkdir -p artifacts/town02-runtime
python(){ printf 'PY %s\\n' "$*"; }
run_test(){ printf 'TEST %s\\n' "$*"; }
adb(){ :; }
sleep(){ :; }
'''
        observed = {}
        for stage in (*handoff.STAGES, 'all'):
            result = subprocess.run([existing_bash(), '-c', prefix + block, 'dispatch-fixture', stage],
                cwd=self.root, capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr[:2000])
            lines = result.stdout.splitlines()
            flows = [line.split()[2] for line in lines if line.startswith('PY tools/record_app_audio.py ')]
            observed[stage] = flows
            if stage != 'all':
                self.assertEqual(stage == 'base', 'TEST testUpgradeKeepsPreviousSave' in lines)
            for line in lines:
                if line.startswith('PY tools/record_app_audio.py world-') and 'world-f0 ' not in line:
                    self.assertIn('--cold-test', line)
        self.assertEqual(observed['all'], sum((observed[s] for s in handoff.STAGES), []))
        self.assertEqual(29, len(observed['all']))
        self.assertEqual(29, len(set(observed['all'])))
        self.assertEqual('world-north-palace', observed['base'][-1])
        self.assertEqual('world-cave85', observed['world'][0])
        self.assertEqual('world-ferry', observed['world'][-1])
        self.assertEqual('world-island', observed['continuation'][0])
        self.assertEqual('world-queen117', observed['continuation'][-1])

    def test_windows_selects_existing_git_bash_instead_of_wsl_shim(self):
        git = self.root / 'Git/cmd/git.exe'
        git.parent.mkdir(parents=True)
        git.touch()
        bash = self.root / 'Git/bin/bash.exe'
        bash.parent.mkdir()
        bash.touch()
        with patch.object(shutil, 'which', return_value=str(git)):
            self.assertEqual(str(bash.resolve()), existing_bash('nt'))

    def test_windows_missing_git_bash_is_not_silently_substituted(self):
        with patch.object(shutil, 'which', return_value=None), \
                patch.dict(os.environ, {'ProgramFiles': str(self.root / 'no-git')}):
            with self.assertRaisesRegex(RuntimeError, 'WSL launcher'):
                existing_bash('nt')


if __name__ == '__main__':
    unittest.main()
