"""Execute the existing safe summary module's separate publication assessment.

No production query, private client samples, Secrets or upload are involved.
"""
import copy,json,subprocess,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]

class RuntimeReleaseDecisionTests(unittest.TestCase):
    def setUp(self):
        self.accepted=json.loads((ROOT/'ci/runtime-nonblocking-issues.json').read_text(encoding='utf-8'))
        issue=self.accepted['issues'][0]
        self.report={'task_id':'WORLD-FULL-01','stage':'preflight','queriedAt':'2026-10-03T16:56:21Z',
            'result':{'status':'ISSUES_FOUND','retention':{'cleanupFailures':0,
                'releaseAuthority':'admin_verified_OSS_publication_metadata','allowedReleases':[
                    {'versionCode':issue['recoveredVersionCode'],'sha256':issue['recoveredReleaseSha256']},
                    {'versionCode':issue['versionCode'],'sha256':issue['releaseSha256']}]},
                'errors':{issue['bucket']:{'count':1,'latestAt':issue['latestAt'],'versionCode':issue['versionCode'],
                    'type':issue['type'],'code':issue['code'],'stage':issue['stage'],'severity':'ERROR'}}}}
        self.issue=issue;self.error=self.report['result']['errors'][issue['bucket']]
    def assess(self):
        script="""import {assessRuntimePublication} from './server/runtime-summary.mjs';
import {readFileSync} from 'node:fs';
const {report,accepted}=JSON.parse(readFileSync(0,'utf8'));const before=JSON.stringify(report);
const decision=assessRuntimePublication(report,accepted,Date.parse('2026-10-03T18:00:00Z'));
if(JSON.stringify(report)!==before)throw Error('Diagnostic mutated');
process.stdout.write(JSON.stringify(decision));"""
        r=subprocess.run(['node','--input-type=module','-e',script],cwd=ROOT,input=json.dumps({'report':self.report,'accepted':self.accepted}),text=True,capture_output=True,check=True)
        return json.loads(r.stdout)
    def test_known_prior_version_interruption_retains_real_error_and_cause(self):
        d=self.assess();self.assertTrue(d['allowed']);self.assertEqual('ISSUES_FOUND',d['diagnosticStatus'])
        self.assertEqual('ALLOW_WITH_KNOWN_NON_BLOCKING_ISSUES',d['status'])
        self.assertEqual('UNCONFIRMED',d['acknowledgedIssues'][0]['rootCause']);self.assertEqual(1,d['acknowledgedIssues'][0]['count'])
        self.assertEqual('ERROR',self.error['severity'])
    def test_clean_and_no_data_remain_distinct_and_allowed(self):
        self.report['result'].pop('errors')
        for status in ('NO_DATA','NO_ISSUES_OBSERVED'):
            self.report['result']['status']=status;d=self.assess();self.assertTrue(d['allowed']);self.assertEqual(status,d['diagnosticStatus'])
    def test_additional_occurrence_or_time_is_not_the_reviewed_issue(self):
        self.error['count']=2;self.assertFalse(self.assess()['allowed'])
        self.error['count']=1;self.error['latestAt']='2026-10-03T17:00:00Z';self.assertFalse(self.assess()['allowed'])
    def test_added_crash_and_unknown_fault_block(self):
        for key in ('app_crash/NullPointerException','save/failed','battle/duplicate_reward','unknown/ProtocolException'):
            self.report['result']['errors'][key]=dict(self.error);self.assertFalse(self.assess()['allowed']);self.report['result']['errors'].pop(key)
    def test_latest_release_error_and_changed_stage_block(self):
        self.error['versionCode']=self.issue['recoveredVersionCode'];self.assertFalse(self.assess()['allowed'])
        self.error['versionCode']=self.issue['versionCode'];self.error['stage']='installation';self.assertFalse(self.assess()['allowed'])
    def test_both_original_and_recovered_release_hashes_are_required(self):
        for index in (0,1):
            old=self.report['result']['retention']['allowedReleases'][index]['sha256']
            self.report['result']['retention']['allowedReleases'][index]['sha256']='0'*64;self.assertFalse(self.assess()['allowed'])
            self.report['result']['retention']['allowedReleases'][index]['sha256']=old
    def test_untrusted_release_retention_cleanup_and_duplicate_release_reject(self):
        r=self.report['result']['retention'];r['cleanupFailures']=1;self.assertFalse(self.assess()['allowed']);r['cleanupFailures']=0
        r['releaseAuthority']='fixture_untrusted';self.assertFalse(self.assess()['allowed']);r['releaseAuthority']='admin_verified_OSS_publication_metadata'
        r['allowedReleases'].append(copy.deepcopy(r['allowedReleases'][0]));self.assertFalse(self.assess()['allowed'])
    def test_stale_future_and_unavailable_query_do_not_become_healthy(self):
        for time in ('2026-01-01T00:00:00Z','2026-12-01T00:00:00Z','invalid'):
            self.report['queriedAt']=time;self.assertFalse(self.assess()['allowed'])
        self.report['queriedAt']='2026-10-03T16:56:21Z'
        for status in ('UNAVAILABLE','NOT_AVAILABLE','UNKNOWN'):
            self.report['result']['status']=status;self.assertFalse(self.assess()['allowed'])
    def test_task_authorization_and_independent_review_are_required(self):
        self.report['task_id']='TOWN-02';self.assertFalse(self.assess()['allowed']);self.report['task_id']='WORLD-FULL-01'
        self.accepted['issues']=[];self.assertFalse(self.assess()['allowed'])
    def test_inconsistent_error_status_count_and_unclassified_issues_reject(self):
        self.report['result']['status']='NO_ISSUES_OBSERVED';self.assertFalse(self.assess()['allowed'])
        self.report['result']['status']='ISSUES_FOUND';self.error['count']=0;self.assertFalse(self.assess()['allowed'])
        self.report['result']['errors']={};self.assertFalse(self.assess()['allowed'])
    def test_no_private_fields_copied_to_decision(self):
        self.error['sample']={'stack':'PRIVATE_STACK','token':'PRIVATE_TOKEN'}
        self.error['privateDetails']='PRIVATE_SAMPLE';d=self.assess();self.assertTrue(d['allowed']);self.assertNotIn('PRIVATE_',json.dumps(d))
    def test_original_workflow_and_direct_publisher_both_assess_before_upload(self):
        workflow=(ROOT/'.github/workflows/android-publish.yml').read_text(encoding='utf-8')
        publisher=(ROOT/'publish-apk.ps1').read_text(encoding='utf-8')
        self.assertLess(workflow.index('--release-assessment reports/runtime-preflight.json'),workflow.index('./publish-apk.ps1 -ApkPath'))
        self.assertIn('--release-assessment reports/runtime-postflight.json',workflow)
        self.assertLess(publisher.index('--release-assessment'),publisher.index('if (-not $env:ALIYUN_ACCESS_KEY_ID'))
        self.assertIn('if($LASTEXITCODE -ne 0)',publisher)

if __name__=='__main__':unittest.main()
