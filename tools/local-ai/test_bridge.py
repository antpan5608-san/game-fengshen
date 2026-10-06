"""Independent workflow checks in disposable Git repositories."""
import json
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from pathlib import Path
from bridge import DRAFT_SYSTEM, LocalUnavailable, SYSTEM, Workspace
from context_tools import OptimizedWorkspace


def response(text='检查过提供的代码，暂无可确认的问题。', finish='stop'):
    return {'choices': [{'message': {'content': text}, 'finish_reason': finish}]}


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'Fixture')
        (self.root / '.gitignore').write_text('.local-ai/\nprivate-inputs/\n')
        (self.root / 'a.py').write_text('def add(a, b):\n    return a+b\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'baseline')
        self.requests = []
        def transport(body):
            self.requests.append(body)
            return response()
        self.w = Workspace(self.root, transport=transport, counter=lambda s: len(s), sleeper=lambda _: None)
        self.w.initialize()

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], stderr=subprocess.DEVNULL)

    def accept(self, report, **kwargs):
        return self.w.decide(report['snapshot'], ['Executed fixture behavior checks'], ['Reviewed local suggestions'], **kwargs)

    def test_commit_does_not_hide_changes(self):
        (self.root / 'a.py').write_text('def add(a,b):\n    return a-b\n')
        self.git('add', 'a.py')
        self.git('commit', '-qm', 'regression')
        self.assertEqual(self.w.status()['changed'], ['a.py'])
        self.w.review('T1', 'check arithmetic')
        self.assertIn('return a-b', self.requests[0]['messages'][1]['content'])

    def test_new_deleted_and_stale_receipt(self):
        (self.root / 'a.py').unlink()
        (self.root / 'new.kt').write_text('fun value() = 1\n')
        report = self.w.review('T2', 'new and deleted files')
        self.assertEqual(set(report['changed']), {'a.py', 'new.kt'})
        (self.root / 'new.kt').write_text('fun value() = 2\n')
        with self.assertRaisesRegex(ValueError, 'stale'):
            self.accept(report)
        report = self.w.review('T2', 'new and deleted files')
        self.accept(report)
        self.assertEqual(self.w.status()['status'], 'current')

    def test_excludes_secrets_and_path_escape(self):
        (self.root / 'private-inputs').mkdir()
        (self.root / 'private-inputs/token.py').write_text('secret')
        (self.root / '.env').write_text('secret')
        (self.root / 'signing.properties').write_text('secret')
        (self.root / 'local.properties').write_text('secret')
        self.assertEqual(self.w.status()['changed'], [])
        for path in ('../outside.py', 'private-inputs/token.py', 'local.properties'):
            with self.assertRaises(ValueError):
                self.w.safe_path(path)

    def test_chunks_preserve_unicode_and_long_line(self):
        text = '中文代码' * 4000 + '\n'
        prefix = '任务\n'
        chunks = self.w.pack(prefix, text)
        restored = ''.join(c.split('（可能是连续 diff 的一部分）\n', 1)[1] for c in chunks)
        self.assertEqual(restored, text)
        self.assertTrue(all(self.w.count(SYSTEM + c) <= 2800 for c in chunks))

    def test_failure_requires_explicit_fallback(self):
        (self.root / 'a.py').write_text('def add(a,b):\n    return a-b\n')
        self.w.transport = lambda _: (_ for _ in ()).throw(LocalUnavailable('network_timeout'))
        report = self.w.review('T3', 'offline service')
        self.assertEqual(report['status'], 'partial')
        with self.assertRaises(ValueError):
            self.accept(report)
        self.accept(report, mode='codex_fallback', reason='Network unavailable; Codex checked every changed function and its tests.')
        self.assertEqual(self.w.status()['decision']['mode'], 'codex_fallback')

    def test_truncated_or_malformed_response_not_passed(self):
        (self.root / 'a.py').write_text('def add(a,b):\n    return a-b\n')
        for result in (response('incomplete', 'length'), {}, response('')):
            self.w.transport = lambda _, r=result: r
            self.assertEqual(self.w.review('T4', 'invalid output')['status'], 'partial')

    def test_hook_plan_nochange_and_bounded_continuation(self):
        self.assertEqual(self.w.stop({})['continue'], True)
        (self.root / 'a.py').write_text('print("changed")\n')
        self.assertEqual(self.w.stop({'permission_mode': 'plan'})['continue'], True)
        self.assertEqual(self.w.stop({})['decision'], 'block')
        self.assertEqual(self.w.stop({})['continue'], False)
        report = self.w.review('T5', 'validate print')
        self.accept(report)
        self.assertEqual(self.w.stop({})['continue'], True)

    def test_cache_and_summary_changes(self):
        (self.root / 'a.py').write_text('print("changed")\n')
        self.w.review('T6', 'review')
        count = len(self.requests)
        report = self.w.review('T6', 'review')
        self.assertEqual(len(self.requests), count)
        self.assertTrue(all(a['cached'] for a in report['analyses']))
        self.accept(report)
        (self.root / 'docs').mkdir()
        (self.root / 'docs/local-ai-context.md').write_text('New project constraints')
        self.assertEqual(self.w.status()['status'], 'pending')

    def test_readonly_draft_and_secret_redaction(self):
        (self.root / 'a.py').write_text('api_key = "PRIVATE_TEST_KEY"\n')
        before = (self.root / 'a.py').read_bytes()
        draft = self.w.draft('make helper', ['a.py'], 'test helper')
        self.assertTrue(draft['advisory_only'])
        self.assertEqual((self.root / 'a.py').read_bytes(), before)
        self.assertNotIn('PRIVATE_TEST_KEY', json.dumps(self.requests))
        with self.assertRaises(ValueError):
            self.w.initialize()

    def test_http_retries_only_queue_full(self):
        class Reply:
            def __enter__(self):
                import io
                return io.BytesIO(json.dumps(response()).encode())
            def __exit__(self, *args):
                return False
        from unittest.mock import Mock
        opener = Mock()
        opener.open.side_effect = [HTTPError('private', 503, 'busy', {}, None),
                                  HTTPError('private', 503, 'busy', {}, None), Reply()]
        with patch('bridge.build_opener', return_value=opener), patch.dict('os.environ', {'LOCAL_AI_API_KEY': 'fixture'}):
            self.assertIn('choices', self.w.http({}))
            self.assertEqual(opener.open.call_count, 3)
            opener.open.reset_mock()
            opener.open.side_effect = HTTPError('private', 401, 'unauthorized', {}, None)
            with self.assertRaisesRegex(LocalUnavailable, 'http_401'):
                self.w.http({})
            self.assertEqual(opener.open.call_count, 1)

    def test_expired_deadline_never_sends_request(self):
        with patch.dict('os.environ', {'LOCAL_AI_API_KEY': 'fixture'}), patch('bridge.build_opener') as opener:
            with self.assertRaisesRegex(LocalUnavailable, 'round_time_budget'):
                self.w.http({}, deadline=0)
            opener.return_value.open.assert_not_called()

    def test_two_clients_share_one_inference_slot(self):
        import threading
        import time
        from concurrent.futures import ThreadPoolExecutor
        gate = threading.Lock()
        active = maximum = 0
        def transport(body):
            nonlocal active, maximum
            with gate:
                active += 1
                maximum = max(maximum, active)
            time.sleep(.1)
            with gate:
                active -= 1
            return response()
        second = Workspace(self.root, transport=transport, counter=len, timeout=3)
        self.w.transport = transport
        with ThreadPoolExecutor(max_workers=2) as pool:
            jobs = [pool.submit(w.ask, task) for w, task in ((self.w, 'first'), (second, 'second'))]
            for job in jobs:
                self.assertEqual(job.result()['finish_reason'], 'stop')
        self.assertEqual(maximum, 1)

    def test_ranged_draft_and_original_line_retrieval(self):
        self.w = OptimizedWorkspace(self.root, transport=self.w.transport, counter=len)
        text = '# irrelevant line\n'*3000 + 'def target(value):\n    return value+1\n'
        (self.root/'large.py').write_text(text)
        matches = self.w.search('target', 3)['matches']
        match = next(m for m in matches if m['path']=='large.py')
        self.assertEqual(match['start_line'], 3001)
        self.assertTrue(match['complete_declaration'])
        draft = self.w.draft('draft a boundary test', [], 'test target(1)',
                             ranges=[{'path':'large.py','start_line':3001,'end_line':3002}])
        self.assertNotIn('irrelevant line', self.requests[-1]['messages'][1]['content'])
        self.assertEqual(self.requests[-1]['messages'][0]['content'], DRAFT_SYSTEM)
        self.assertEqual(draft['sources'][0]['start_line'],3001)
        with self.assertRaises(ValueError):
            self.w.draft('bad range', [], '', ranges=[{'path':'large.py','start_line':0,'end_line':3}])

    def test_cache_invalidation_for_authority_changes(self):
        self.w = OptimizedWorkspace(self.root, transport=self.w.transport, counter=len)
        first = self.w.ask('same question')
        self.assertFalse(first['cached'])
        self.assertTrue(self.w.ask('same question')['cached'])
        (self.root/'docs').mkdir()
        (self.root/'docs/current-task.md').write_text('status: pending\n')
        self.assertFalse(self.w.ask('same question')['cached'])
        self.assertEqual(len(self.requests),2)

    def test_packed_review_reserves_identity_overhead(self):
        self.w = OptimizedWorkspace(self.root, transport=self.w.transport, counter=len)
        chunks = self.w.pack('Review this diff\n', '中文代码'*3000)
        for chunk in chunks:
            self.w.ask(chunk)
        self.assertTrue(all(len(b['messages'][0]['content']+b['messages'][1]['content'])<=2800 for b in self.requests))

    def test_concurrent_review_reports_remain_complete(self):
        import os
        import threading
        from concurrent.futures import ThreadPoolExecutor
        gate = threading.Barrier(2)
        commit_lock = threading.Lock()
        replace = os.replace
        def commit(source, destination):
            gate.wait(timeout=3)
            with commit_lock:
                return replace(source, destination)
        snapshot = 'a'*64
        one = {'snapshot': snapshot, 'owner': 'one', 'data': ['one']*100}
        two = {'snapshot': snapshot, 'owner': 'two', 'data': ['two']*100}
        with patch('bridge.os.replace', side_effect=commit), ThreadPoolExecutor(max_workers=2) as pool:
            jobs = [pool.submit(self.w.report, payload) for payload in (one, two)]
            for job in jobs:
                job.result()
        result = json.loads((self.w.state/'reviews'/f'{snapshot}.json').read_text(encoding='utf-8'))
        self.assertIn(result, (one, two))


if __name__ == '__main__':
    unittest.main(verbosity=2)
