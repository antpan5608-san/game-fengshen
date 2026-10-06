import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from context_tools import OptimizedWorkspace, spans


class ContextTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        subprocess.run(['git','init','-q',str(self.root)],check=True)
        (self.root/'docs').mkdir()
        (self.root/'docs/local-ai-context.md').write_text('Kotlin game; suggestions only.',encoding='utf-8')
        (self.root/'docs/current-task.md').write_text('task_id: GAME-01\nstatus: IN_PROGRESS\nstable_version: v82',encoding='utf-8')
        (self.root/'AGENTS.md').write_text('No invented game rules.',encoding='utf-8')
        (self.root/'a.py').write_text('def reward(flags, event):\n    if event in flags:\n        return False\n    flags.add(event)\n    return True\n\ndef other():\n    return 0\n',encoding='utf-8')
        self.calls=[]
        def transport(body):
            self.calls.append(body)
            return {'choices':[{'message':{'content':'draft'},'finish_reason':'stop'}]}
        self.workspace=OptimizedWorkspace(self.root,transport=transport,counter=lambda s:len(s)//3)

    def test_search_complete_python_function(self):
        matches=self.workspace.search('reward')['matches']
        self.assertEqual(matches[0]['path'],'a.py')
        self.assertEqual((matches[0]['start_line'],matches[0]['end_line']),(1,5))
        self.assertTrue(matches[0]['complete_declaration'])
        self.assertNotIn('def other',matches[0]['text'])

    def test_fragment_draft_preserves_line_numbers(self):
        result=self.workspace.draft('add tests',[],'duplicate event returns false',ranges=[dict(path='a.py',start_line=1,end_line=5)])
        prompt=self.calls[0]['messages'][1]['content']
        self.assertIn('a.py:1-5',prompt)
        self.assertNotIn('def other',prompt)
        self.assertEqual(result['sources'][0]['start_line'],1)

    def test_invalid_ranges_and_private_sources(self):
        (self.root/'.secrets').mkdir();(self.root/'.secrets/a.py').write_text('secret')
        for relative in ('../a.py','.secrets/a.py'):
            with self.assertRaises(ValueError):self.workspace.draft('x',[relative],'x')
        for a,b in ((0,1),(2,1),(1,100)):
            with self.assertRaises(ValueError):
                self.workspace.draft('x',[],'x',ranges=[dict(path='a.py',start_line=a,end_line=b)])
        self.assertFalse(any('.secrets' in m['path'] for m in self.workspace.search('secret')['matches']))

    def test_documents_and_model_identity_invalidate_cache(self):
        one=self.workspace.draft('x',['a.py'],'x')
        self.assertFalse(one['cached'])
        self.assertTrue(self.workspace.draft('x',['a.py'],'x')['cached'])
        (self.root/'AGENTS.md').write_text('Updated rules')
        self.assertFalse(self.workspace.draft('x',['a.py'],'x')['cached'])
        self.workspace.model_identity=lambda _:dict(id='same',created=2)
        self.assertFalse(self.workspace.draft('x',['a.py'],'x')['cached'])

    def test_latest_task_used_instead_of_readme(self):
        (self.root/'README.md').write_text('stable_version: v22')
        context=self.workspace.project_context()
        self.assertIn('v82',context);self.assertNotIn('v22',context)
        (self.root/'docs/current-task.md').write_text('task_id: GAME-02\nstatus: BLOCKED')
        self.assertIn('GAME-02',self.workspace.project_context())

    def test_oversized_function_not_silently_truncated(self):
        (self.root/'large.kt').write_text('fun giant() {\n'+('val x = 1\n'*2000)+'}\n')
        with self.assertRaisesRegex(ValueError,'2600'):
            self.workspace.draft('x',['large.kt'],'x')

    def test_kotlin_literal_braces_do_not_cut_function(self):
        text='fun f() {\n val s = "}"\n // }\n println(s)\n}\nfun g() = 2\n'
        self.assertIn((1,5),spans(text,'.kt'))

    def test_source_edit_changes_receipt_and_cached_prompt(self):
        old=self.workspace.draft('x',['a.py'],'x')
        (self.root/'a.py').write_text('def reward():\n    return False\n')
        new=self.workspace.draft('x',['a.py'],'x')
        self.assertNotEqual(old['sources'][0]['sha256'],new['sources'][0]['sha256'])
        self.assertFalse(new['cached'])

    def test_empty_query_limit_and_binary(self):
        with self.assertRaises(ValueError):self.workspace.search('')
        with self.assertRaises(ValueError):self.workspace.search('reward',11)
        (self.root/'binary.py').write_bytes(b'\x00binary')
        with self.assertRaises(ValueError):self.workspace.draft('x',['binary.py'],'x')

    def test_verified_parameters_are_sent_and_invalidate_cache(self):
        self.workspace.draft('x',['a.py'],'x')
        profile=dict(status='candidate_verified',model='local-code:latest',parameters=dict(temperature=.7,top_p=.8,presence_penalty=1.5))
        (self.workspace.state/'active-profile.json').write_text(json.dumps(profile))
        result=self.workspace.draft('x',['a.py'],'x')
        self.assertFalse(result['cached'])
        self.assertEqual(self.calls[-1]['temperature'],.7)
        profile['parameters']['temperature']=True
        (self.workspace.state/'active-profile.json').write_text(json.dumps(profile))
        with self.assertRaises(ValueError):self.workspace.draft('x',['a.py'],'x')


if __name__=='__main__':unittest.main()
