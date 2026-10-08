"""Synthetic metadata/ZIP checks; no Actions, signing or App acceptance claim."""
import copy
import hashlib
import io
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile
from tools import runtime_artifact_selection as selector


class RuntimeArtifactSelectionTests(unittest.TestCase):
    def setUp(self):
        self.source='a'*40
        self.run=dict(status='completed',conclusion='success',event='workflow_dispatch',headSha=self.source,
            headBranch='main',workflowName='Android cloud build',jobs=[
                dict(name='build',status='completed',conclusion='success',startedAt='2026-10-08T00:00:00Z',completedAt='2026-10-08T00:10:00Z'),
                dict(name='runtime',status='completed',conclusion='success',startedAt='2026-10-08T00:30:00Z',completedAt='2026-10-08T00:40:00Z')])
        def artifact(aid,name,time):return dict(id=aid,name=name,created_at=time,expired=False,size_in_bytes=100,
            digest='sha256:'+'b'*64,workflow_run=dict(id=123,head_sha=self.source,head_branch='main'))
        self.listing=dict(total_count=3,artifacts=[artifact(500,'fengshen-signed-apk','2026-10-08T00:09:00Z'),
            artifact(900,'fengshen-town02-runtime-evidence','2026-10-08T00:20:00Z'),
            artifact(200,'fengshen-town02-runtime-evidence','2026-10-08T00:39:00Z')])

    def select(self):return selector.select(self.run,self.listing,123,self.source,'runtime')

    def test_retry_uses_successful_job_upload_even_when_failed_upload_has_larger_id(self):
        before=copy.deepcopy(self.listing);self.assertEqual(200,self.select()['runtime']['id'])
        self.assertEqual(500,self.select()['signed']['id']);self.assertEqual(before,self.listing)

    def test_failure_wrong_source_branch_event_or_duplicate_job_refused(self):
        original=copy.deepcopy(self.run)
        for key,value in [('conclusion','failure'),('headSha','c'*40),('headBranch','other'),('event','push')]:
            self.run=dict(original,**{key:value})
            with self.assertRaises(ValueError):self.select()
        self.run=copy.deepcopy(original);self.run['jobs'].append(copy.deepcopy(self.run['jobs'][-1]))
        with self.assertRaises(ValueError):self.select()
        self.run=copy.deepcopy(original);self.run['jobs'][-1]['conclusion']='failure'
        with self.assertRaises(ValueError):self.select()

    def test_expired_wrong_run_wrong_source_missing_digest_and_outside_interval_refused(self):
        original=copy.deepcopy(self.listing)
        for key,value in [('expired',True),('digest',''),('created_at','2026-10-08T00:40:01Z')]:
            self.listing=copy.deepcopy(original);self.listing['artifacts'][-1][key]=value
            with self.assertRaises(ValueError):self.select()
        for key,value in [('id',999),('head_sha','c'*40),('head_branch','other')]:
            self.listing=copy.deepcopy(original);self.listing['artifacts'][-1]['workflow_run'][key]=value
            with self.assertRaises(ValueError):self.select()

    def test_ambiguous_upload_incomplete_listing_and_bad_timestamp_refused(self):
        self.listing['artifacts'].append(dict(self.listing['artifacts'][-1],id=300));self.listing['total_count']=4
        with self.assertRaises(ValueError):self.select()
        self.listing['total_count']=5
        with self.assertRaisesRegex(ValueError,'Incomplete'):self.select()
        with self.assertRaises(ValueError):selector.instant('2026-10-08T00:00:00')

    def test_stable_requires_final_continuation_upload(self):
        with self.assertRaises(ValueError):selector.select(self.run,self.listing,123,self.source,'runtime-continuation')
        self.run['jobs'][-1]['name']='runtime-continuation'
        self.assertEqual(200,selector.select(self.run,self.listing,123,self.source,'runtime-continuation')['runtime']['id'])

    def zip_bytes(self,name='town02-runtime/runtime-receipt.json'):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w')as archive:archive.writestr(name,b'explicit synthetic evidence')
        raw=stream.getvalue();return raw,dict(digest='sha256:'+hashlib.sha256(raw).hexdigest(),size_in_bytes=len(raw))

    def test_exact_zip_digest_and_evidence_preservation(self):
        raw,artifact=self.zip_bytes()
        with tempfile.TemporaryDirectory()as folder:
            target=Path(folder)/'review';selector.extract_verified(raw,artifact,target)
            self.assertEqual(b'explicit synthetic evidence',(target/'town02-runtime/runtime-receipt.json').read_bytes())
            with self.assertRaisesRegex(ValueError,'overwrite'):selector.extract_verified(raw,artifact,target)
            with self.assertRaisesRegex(ValueError,'ZIP bytes'):selector.extract_verified(raw+b'changed',artifact,Path(folder)/'other')

    def test_zip_path_escape_symlink_and_duplicate_refused_before_extract(self):
        for name in ('../escape','/absolute','C:drive','nested\\escape','./alias'):
            raw,artifact=self.zip_bytes(name)
            with tempfile.TemporaryDirectory()as folder:
                with self.assertRaises(ValueError):selector.extract_verified(raw,artifact,Path(folder)/'review')
                self.assertFalse((Path(folder)/'review').exists())
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w')as archive:
            link=zipfile.ZipInfo('link');link.external_attr=0o120777<<16;archive.writestr(link,b'../escape')
        raw=stream.getvalue()
        with tempfile.TemporaryDirectory()as folder:
            with self.assertRaises(ValueError):selector.extract_verified(raw,dict(digest='sha256:'+hashlib.sha256(raw).hexdigest(),size_in_bytes=len(raw)),Path(folder)/'review')
        stream=io.BytesIO()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            with zipfile.ZipFile(stream,'w')as archive:
                archive.writestr('receipt.json',b'first');archive.writestr('receipt.json',b'second')
        raw=stream.getvalue()
        with tempfile.TemporaryDirectory()as folder:
            with self.assertRaisesRegex(ValueError,'Duplicate'):
                selector.extract_verified(raw,dict(digest='sha256:'+hashlib.sha256(raw).hexdigest(),size_in_bytes=len(raw)),Path(folder)/'review')


if __name__=='__main__':unittest.main()
