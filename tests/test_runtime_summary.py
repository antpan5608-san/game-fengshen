"""Run the actual Node report projection on synthetic server-schema responses.

No server, credentials, persisted client logs, or PowerShell runtime required.
"""
import copy
import json
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'server/runtime-summary.mjs'


class RuntimeSummaryTests(unittest.TestCase):
    def setUp(self):
        self.event = dict(eventID='PRIVATE_EVENT_ID', sessionID='PRIVATE_SESSION_ID',
                          batchID='PRIVATE_BATCH_ID', stack='PRIVATE_STACK',
                          versionCode=26, versionName='0.8.6-fixture', type='apk_update',
                          code='ProtocolException', severity='ERROR',
                          device=dict(model='PRIVATE_DEVICE', emulator=False),
                          details=dict(stage='download_or_verify', targetVersion=27,
                                       installedVersion=26, installSessionID='PRIVATE_INSTALL_ID',
                                       audioCause='PRIVATE_CAUSE', reason='PRIVATE_REASON',
                                       future=dict(secret='PRIVATE_FUTURE')))
        self.response = dict(status='ISSUES_FOUND', queriedAt='2026-01-02T00:00:00Z',
                             eventCount=3500, sessionCount=3, emulatorSessions=0,
                             realDeviceSessions=3, testEventCount=0,
                             queryRange=dict(firstReceivedAt='2026-01-01T00:00:00Z',
                                             lastReceivedAt='2026-01-02T00:00:00Z'),
                             coverageLimits=['Uploaded sessions only'],
                             errors={'apk_update/ProtocolException': dict(count=1,
                                     latestAt='2026-01-01T00:00:01Z', sample=self.event,
                                     privateFuture='PRIVATE_ERROR_FIELD')},
                             samples=[self.event], futureRawEvents=[self.event],
                             contents={'': '', 'content-fixture': 'a' * 64},
                             retention=dict(allowedReleases=[dict(versionCode=27,
                                 versionName='0.8.7-fixture', sha256='b' * 64,
                                 publishedAt='2026-01-01T00:00:00Z', privateFuture='PRIVATE_RELEASE_FIELD')],
                                 storedVersionCounts={'27': 1270, '26': 2230}, cleanupFailures=0,
                                 releaseAuthority='admin_verified_OSS_publication_metadata',
                                 cleanedEvents=100, droppedByVersion={'25': 10},
                                 perVersionCapacityBytes=20000000, privateStorage=True,
                                 receiptCount=20, futureRawEvents=[self.event]))

    def run_projection(self, response, summary_only=False):
        result = subprocess.run(['node', str(SCRIPT)] + (['--summary-only'] if summary_only else []),
                                input=json.dumps(response), text=True, capture_output=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def test_both_public_modes_remove_nested_and_top_level_private_events(self):
        for summary_only in (False, True):
            with self.subTest(summary_only=summary_only):
                output = self.run_projection(self.response, summary_only)
                self.assertNotIn('PRIVATE_', json.dumps(output))
                self.assertNotIn('samples', output)
                self.assertEqual(output['errors']['apk_update/ProtocolException'], dict(
                    count=1, latestAt='2026-01-01T00:00:01Z', type='apk_update',
                    code='ProtocolException', severity='ERROR', versionCode=26,
                    versionName='0.8.6-fixture', stage='download_or_verify',
                    targetVersion=27, installedVersion=26, emulator=False))

    def test_error_buckets_counts_and_status_never_acknowledged_or_downgraded(self):
        self.response['errors']['historical_process_exit/anr'] = dict(count=9,
            latestAt='2026-01-01T00:00:02Z', sample=dict(type='historical_process_exit',
            code='anr', severity='WARN', versionCode=27, stack='PRIVATE_HISTORY'))
        for summary_only in (False, True):
            output = self.run_projection(self.response, summary_only)
            self.assertEqual(output['status'], 'ISSUES_FOUND')
            self.assertEqual(set(output['errors']), set(self.response['errors']))
            for key, error in self.response['errors'].items():
                self.assertEqual(output['errors'][key]['count'], error['count'])
                self.assertEqual(output['errors'][key]['latestAt'], error['latestAt'])

    def test_authority_releases_counts_and_coverage_preserved(self):
        output = self.run_projection(self.response, True)
        for key in ('status', 'queriedAt', 'queryRange', 'eventCount', 'sessionCount',
                    'emulatorSessions', 'realDeviceSessions', 'testEventCount', 'coverageLimits'):
            self.assertEqual(output[key], self.response[key])
        expected = copy.deepcopy(self.response['retention'])
        del expected['allowedReleases'][0]['privateFuture']
        for key in ('allowedReleases', 'cleanupFailures', 'storedVersionCounts', 'releaseAuthority'):
            self.assertEqual(output['retention'][key], expected[key])

    def test_unavailable_no_data_and_healthy_states_stay_distinct(self):
        for status in ('UNAVAILABLE', 'NOT_AVAILABLE', 'NO_DATA', 'NO_ISSUES_OBSERVED'):
            response = dict(status=status, reason='fixture_reason', httpStatus=403)
            self.assertEqual(self.run_projection(response, True), response)

    def test_default_keeps_only_extra_aggregates_not_raw_diagnostics(self):
        output = self.run_projection(self.response)
        summary = self.run_projection(self.response, True)
        self.assertEqual(output['contents'], self.response['contents'])
        self.assertEqual(output['retention']['droppedByVersion'], {'25': 10})
        self.assertNotIn('contents', summary)
        self.assertNotIn('droppedByVersion', summary['retention'])

    def test_malformed_payloads_fail_without_echoing_private_input(self):
        for payload in ('{"PRIVATE_PARSE_ERROR":', json.dumps(dict(status='ISSUES_FOUND',
                        errors={'PRIVATE_ERROR': dict(count='PRIVATE_COUNT')})),
                        json.dumps(dict(status='ISSUES_FOUND', errors=['PRIVATE_ARRAY']))):
            result = subprocess.run(['node', str(SCRIPT)], input=payload,
                                    text=True, capture_output=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, '')
            self.assertEqual(result.stderr.strip(), 'Runtime summary projection failed')

    def test_powershell_output_boundary_and_existing_publication_gate_are_wired(self):
        # Structural assertion only: the actual PowerShell execution remains a
        # runner check, since this portable test environment has no pwsh.
        script = (ROOT / 'check-runtime.ps1').read_text()
        self.assertIn("'server/runtime-summary.mjs') @projection", script)
        self.assertIn('($safe -join "`n")|ConvertFrom-Json -AsHashtable', script)
        self.assertNotIn('($raw -join "`n")|ConvertFrom-Json', script)
        self.assertIn("status='UNAVAILABLE'", script)
        workflow = (ROOT / '.github/workflows/android-publish.yml').read_text()
        self.assertIn("--release-assessment reports/runtime-preflight.json ci/runtime-nonblocking-issues.json", workflow)
        self.assertIn('Runtime inspection unavailable or unresolved new/unknown/blocking errors; upload prohibited', workflow)


if __name__ == '__main__':
    unittest.main()
