"""Isolated transport/receipt rejection tests; these are not Android gameplay."""
import copy
import base64
import contextlib
import io
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
        scope_patch = patch.object(handoff, "SCOPE_PATH", self.root / "no-scope.json")
        scope_patch.start()
        self.addCleanup(scope_patch.stop)
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

    def test_isolated_import_uses_app_uid_and_preserves_exact_normal_bytes(self):
        _, output = self.make_bundle()
        save = output / 'world-north-palace-expected-save.json'
        sha = handoff.digest(save)
        with patch.object(handoff.subprocess, 'check_output', side_effect=[b'ranchu', (sha + ' file').encode()]), \
                patch.object(handoff.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, b'OK (1 test)', b'')) as adb:
            receipt = self.root / 'previous.json'
            handoff.import_to_isolated_avd(output, 'world', self.candidate, receipt)
            calls = [call.args[0] for call in adb.call_args_list]
            imports = [call for call in calls if 'instrument' in call]
            self.assertEqual(1, len(imports))
            command = imports[0]
            self.assertEqual(save.read_bytes(), base64.b64decode(command[command.index('checkpointBytesBase64') + 1]))
            self.assertEqual(sha, command[command.index('checkpointSha256') + 1])
            self.assertEqual(save.name, command[command.index('checkpointName') + 1])
            self.assertFalse(any('push' in call or 'mkdir' in call or 'chmod' in call or 'chown' in call for call in calls))
            self.assertTrue(all(call[:3] == ['adb', '-s', 'emulator-5554'] for call in calls))
            self.assertFalse(any('shared_prefs' in str(call) or 'clear' in call for call in calls))
            self.assertEqual(['base'], handoff.read_json(receipt)['completedStages'])

    def test_app_read_failure_cannot_issue_successful_handoff_receipt(self):
        _, output = self.make_bundle()
        failed = subprocess.CompletedProcess([], 0, b'FAILURES!!! EACCES', b'')
        with patch.object(handoff.subprocess, 'check_output', return_value=b'ranchu'), \
                patch.object(handoff.subprocess, 'run', return_value=failed):
            receipt = self.root / 'previous.json'
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, 'App-owned checkpoint'):
                handoff.import_to_isolated_avd(output, 'world', self.candidate, receipt)
            self.assertFalse(receipt.exists())

    def test_transport_rejects_oversize_and_traversal_before_adb(self):
        with patch.object(handoff.subprocess, 'run') as adb:
            for name, data in [('world-north-palace-expected-save.json', b'x' * 65537),
                               ('../world-north-palace-expected-save.json', b'{}'),
                               ('world-runtime-storage-probe-expected-save.json', b'')]:
                with self.assertRaisesRegex(ValueError, 'name or size'):
                    handoff.write_checkpoint_as_app(['adb', '-s', 'emulator-5554'], name, data, self.candidate)
            adb.assert_not_called()

    def test_app_success_with_changed_readback_bytes_still_fails(self):
        _, output = self.make_bundle()
        with patch.object(handoff.subprocess, 'check_output', side_effect=[b'ranchu', b'0' * 64 + b' file']), \
                patch.object(handoff.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, b'OK (1 test)', b'')):
            with self.assertRaisesRegex(ValueError, 'changed bytes'):
                handoff.import_to_isolated_avd(output, 'world', self.candidate, self.root / 'previous.json')
            self.assertFalse((self.root / 'previous.json').exists())

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

    def test_development_rollback_keeps_loader_failure_and_cold_gates(self):
        script = (Path(__file__).resolve().parents[1] / 'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
        start = script.index('if [[ "$stage" == development-smoke ]]; then\n    development_scope=')
        block = script[start:script.index('\nbase=(', start)]
        prefix = '''set -euo pipefail
stage=development-smoke
mkdir -p artifacts/town02-runtime
adb(){ printf 'ADB %s\\n' "$*" >> calls.txt; if [[ "$*" == *"am instrument"* ]]; then [[ "${FAIL_LOADER:-0}" == 0 ]] && printf 'OK (1 test)\\n' || printf 'FAILURES!!!\\n'; fi; }
run_test(){ printf 'TEST %s\\n' "$*" >> calls.txt; [[ "$1" != testTouchUxSelectionScrollAndAtomicEquipment || "${FAIL_TOUCH:-0}" != 1 ]]; }
python(){ printf 'PY %s\\n' "$*" >> calls.txt; [[ "${FAIL_ROLLBACK:-0}" == 0 || "${2:-none}" != world-save-history ]] && [[ "${FAIL_PERSONAL:-0}" == 0 || "${2:-none}" != personal-r1-smoke ]]; }
pull_evidence(){ printf 'PULL\\n' >> calls.txt; }
'''
        path = self.root / 'development-rollback.sh'
        path.write_text(prefix + block, encoding='utf-8', newline='\n')
        for scope, fail_loader, fail_rollback, fail_touch, fail_personal, success in [
                ('full', '0', '0', '0', '0', True), ('rollback', '0', '0', '0', '0', True),
                ('rollback', '1', '0', '0', '0', False), ('rollback', '0', '1', '0', '0', False),
                ('rollback', '0', '0', '1', '0', False), ('rollback', '0', '0', '0', '1', False),
                ('invalid', '0', '0', '0', '0', False)]:
            with self.subTest(scope=scope, loader=fail_loader, rollback=fail_rollback):
                calls = self.root / 'calls.txt'; calls.unlink(missing_ok=True)
                result = subprocess.run([existing_bash(), path.as_posix()], cwd=self.root,
                    env=dict(os.environ, FENGSHEN_DEVELOPMENT_SMOKE_SCOPE=scope,
                             FAIL_LOADER=fail_loader, FAIL_ROLLBACK=fail_rollback, FAIL_TOUCH=fail_touch, FAIL_PERSONAL=fail_personal),
                    capture_output=True, text=True, timeout=10)
                self.assertEqual(success, result.returncode == 0, result.stderr)
                observed = calls.read_text() if calls.exists() else ''
                if scope == 'invalid': self.assertEqual('', observed)
                if scope == 'rollback': self.assertNotIn('FiveMinuteAutoSave', observed)
                if scope == 'full': self.assertIn('FiveMinuteAutoSave false', observed)
                if success:
                    self.assertIn('ContentTest#testControlledWell8LocationItemPendingCodecAndNoDuplicateCompletion', observed)
                    self.assertIn('TEST testTouchUxSelectionScrollAndAtomicEquipment false', observed)
                    self.assertIn('TEST testControlledR1ReplayVersionMarkerBounds false', observed)
                    self.assertIn('TEST testControlledPlayableR1MedicalDoorReentryFromVerifiedSave false', observed)
                    self.assertIn('--cold-test testPersonalR1SmokeColdRestartMatchesVerifiedSave', observed)
                    self.assertIn('--cold-test testSaveHistoryExternalColdStartMatchesRestoredSnapshot', observed)
                    self.assertIn('PULL', observed)
                else: self.assertNotIn('PULL', observed)
                if fail_touch == '1': self.assertNotIn('PY tools/record_app_audio.py', observed)
                if fail_personal == '1': self.assertNotIn('PY tools/record_app_audio.py world-save-history', observed)
                if fail_rollback == '1': self.assertIn('logcat -d -b crash -s AndroidRuntime', observed)

    def test_actual_single_test_runner_passes_restore_mode_and_rejects_invalid_mode(self):
        script = (Path(__file__).resolve().parents[1] / 'ci/run-town02-runtime.sh').read_text()
        start = script.index('run_test(){')
        block = script[start:script.index('\nif [[ "$stage" == development-smoke ]]; then', start)]
        prefix = '''set -euo pipefail
mkdir -p artifacts/town02-runtime
timeout(){ shift; "$@"; }
adb(){ printf '%s\\n' "$*" >> adb-calls.txt; printf 'OK (1 test)\\n'; }
'''
        fixture = self.root / 'fixture-retention.sh'
        fixture.write_text(prefix + block + '\nrun_test fixtureDefault\nrun_test fixtureReplay false\n',
                           encoding='utf-8', newline='\n')
        run = subprocess.run([existing_bash(), fixture.as_posix()], cwd=self.root,
                             capture_output=True, text=True, timeout=10)
        self.assertEqual(0, run.returncode, run.stderr)
        calls = (self.root / 'adb-calls.txt').read_text().splitlines()
        self.assertEqual(2, len(calls))
        self.assertIn('-e keepFixtureForRestart true -e class org.fengshen.dev.TouchTest#fixtureDefault', calls[0])
        self.assertIn('-e keepFixtureForRestart false -e class org.fengshen.dev.TouchTest#fixtureReplay', calls[1])
        (self.root / 'adb-calls.txt').unlink()
        fixture.write_text(prefix + block + '\nrun_test fixtureInvalid unknown\n', encoding='utf-8', newline='\n')
        run = subprocess.run([existing_bash(), fixture.as_posix()], cwd=self.root,
                             capture_output=True, text=True, timeout=10)
        self.assertNotEqual(0, run.returncode)
        self.assertFalse((self.root / 'adb-calls.txt').exists())

    def test_actual_shell_stage_dispatch_keeps_every_normal_and_cold_test(self):
        # Execute the actual dispatch block against harmless shell functions;
        # no SDK/AVD/GitHub or App state is accessed by this transport test.
        root = Path(__file__).resolve().parents[1]
        script = (root / 'ci/run-town02-runtime.sh').read_text()
        start = script.index('if [[ "$stage" == all || "$stage" == base ]]; then\nrun_test testUpgradeKeepsPreviousSave')
        block = script[start:script.index('# Clinical sub-results', start)]
        prefix = '''set -euo pipefail
stage="$1"
scope_id="WORLD-FULL-01"
quality="STABLE"
mkdir -p artifacts/town02-runtime
python(){ printf 'PY %s\\n' "$*"; }
run_test(){ printf 'TEST %s\\n' "$*"; }
adb(){ :; }
sleep(){ :; }
'''
        observed = {}
        fixture = self.root / 'dispatch-fixture.sh'
        fixture.write_text(prefix + block, encoding='utf-8', newline='\n')
        for stage in (*handoff.STAGES, 'all'):
            result = subprocess.run([existing_bash(), fixture.as_posix(), stage],
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
        self.assertEqual('world-village1', observed['base'][-1])
        self.assertLess(observed['base'].index('world-north-palace'), observed['base'].index('world-north'))
        self.assertEqual('world-cave85', observed['world'][0])
        self.assertEqual('world-ferry', observed['world'][-1])
        self.assertEqual('world-island', observed['continuation'][0])
        self.assertEqual('world-queen117', observed['continuation'][-1])

    def test_actual_r1_dispatch_freezes_endpoint_keeps_shared_tests_and_cold_paths(self):
        root = Path(__file__).resolve().parents[1]
        script = (root / 'ci/run-town02-runtime.sh').read_text()
        start = script.index('if [[ "$stage" == all || "$stage" == base ]]; then\nrun_test testUpgradeKeepsPreviousSave')
        block = script[start:script.index('# Clinical sub-results', start)]
        prefix = '''set -euo pipefail
stage="$1"
scope_id="PLAYABLE-R1"
quality="STABLE"
mkdir -p artifacts/town02-runtime
python(){ printf 'PY %s\\n' "$*"; }
run_test(){ printf 'TEST %s\\n' "$*"; }
adb(){ :; }
sleep(){ :; }
'''
        observed = {}
        # Git Bash -c transport truncated the >8KiB block on the Windows runner.
        # Execute the same full block from LF/UTF-8 bytes, with no omitted gates.
        fixture = self.root / 'r1-dispatch-fixture.sh'
        fixture.write_text(prefix + block, encoding='utf-8', newline='\n')
        for stage in handoff.STAGES:
            result = subprocess.run([existing_bash(), fixture.as_posix(), stage],
                cwd=self.root, capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr[:2000])
            lines = result.stdout.splitlines()
            observed[stage] = [line.split()[2] for line in lines if line.startswith('PY tools/record_app_audio.py ')]
            for line in lines:
                if line.startswith('PY tools/record_app_audio.py world-') and 'world-f0 ' not in line:
                    self.assertIn('--cold-test', line)
            if stage == 'base':
                self.assertIn('TEST testControlledPlayableR1MedicalDoorReentryFromVerifiedSave false', lines)
                self.assertIn('TEST testControlledR1VillageTwoPoisonSupplyAndInnFromVerifiedSave false', lines)
                self.assertIn('TEST testControlledNorthTravelFromVerifiedPalaceSave', lines)
                for label, method in [('world-north','testNormalWorldSeaNorthFromVerifiedNorthPalaceSave'),
                                      ('world-village1','testNormalWorldVillageOneServicesFromVerifiedNorthPalaceSave')]:
                    self.assertTrue(any(line.startswith('PY tools/record_app_audio.py '+label+' '+method+' ') for line in lines))
                for test in ['testUpgradeKeepsPreviousSave','testTouchUxTradeGesturesAndResultEquivalence',
                             'testControlledMedicalCommandsCancellationGestureAndSave',
                             'testControlledWholly08PartyAdvancesWithoutTouchCommand',
                             'testInput01RealMapWallSlidesAndMenuCancellation']:
                    self.assertIn('TEST '+test, lines)
        self.assertEqual(7,len(observed['base']))
        self.assertLess(observed['base'].index('world-north-palace'), observed['base'].index('world-north'))
        self.assertLess(observed['base'].index('world-north-palace'), observed['base'].index('world-village1'))
        self.assertEqual(['world-cave85','world-east-palace','world-hell-village2'],observed['world'])
        self.assertEqual(['world-r1-medical'],observed['continuation'])
        self.assertNotIn('world-first-hall',sum(observed.values(),[]))

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
