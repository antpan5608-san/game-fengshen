"""Strict read-only evidence boundaries across real XML replacement failure modes."""
import json,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from record_app_audio import read_saved_boundary,SavedBoundaryUnavailable,validate_recording_budget,recording_result,assert_cold_boundary

class RecordingBoundaryTests(unittest.TestCase):
    def test_cold_boundary_keeps_actual_states_and_rejects_even_a_false_flag_addition(self):
        import tempfile
        before={'mapId':7,'flags':{'four':True},'characters':[{'id':'jiangziya','hp':1608}]}
        after=dict(before,flags={'four':True,'three':False})
        with tempfile.TemporaryDirectory()as tmp:
            path=Path(tmp)/'cold.json'
            with self.assertRaisesRegex(AssertionError,'Cold restart changed saved state'):
                assert_cold_boundary(before,after,path)
            evidence=json.loads(path.read_text(encoding='utf-8'))
            self.assertEqual(before,evidence['before']);self.assertEqual(after,evidence['after'])
            self.assertEqual(['flags'],evidence['differentTopLevelFields']);self.assertFalse(evidence['equal'])
            assert_cold_boundary(before,before,path)
            self.assertTrue(json.loads(path.read_text(encoding='utf-8'))['equal'])
    def test_jiang_controlled_kind_retains_explicit_normal_route_and_exploration_limits(self):
        result=recording_result([],[],True,'CONTROLLED_JIANG_INVITATION_SMOKE')
        self.assertEqual('CONTROLLED_JIANG_INVITATION_SMOKE',result['kind'])
        self.assertEqual('NOT_APPLICABLE',result['normalAssertions'])
        self.assertEqual('PASS',result['controlledAssertions'])
        self.assertFalse(result['continuedExploration'])
        with self.assertRaises(ValueError):recording_result([],[],True,'NORMAL_WORLD_PASS')
    def test_controlled_rollback_recording_cannot_claim_normal_route_or_continued_exploration(self):
        normal=recording_result([],[],False)
        controlled=recording_result([],[],True)
        self.assertEqual('PASS',normal['normalAssertions'])
        self.assertTrue(normal['continuedExploration'])
        self.assertEqual('NOT_APPLICABLE',controlled['normalAssertions'])
        self.assertEqual('PASS',controlled['controlledAssertions'])
        self.assertEqual('CONTROLLED_SAVE_HISTORY_SMOKE',controlled['kind'])
        self.assertFalse(controlled['continuedExploration'])
        self.assertTrue(controlled['forceStopRestartEqual'])
    def test_battle_magic_recording_keeps_controlled_limits(self):
        result=recording_result([],[],True,'CONTROLLED_BATTLE_MAGIC_SMOKE')
        self.assertEqual('CONTROLLED_BATTLE_MAGIC_SMOKE',result['kind'])
        self.assertEqual('NOT_APPLICABLE',result['normalAssertions'])
        self.assertEqual('PASS',result['controlledAssertions'])
        self.assertFalse(result['continuedExploration'])
    def test_only_scoped_first_preparation_and_hall_batch_have_a_longer_bounded_budget(self):
        for prefix,budget in [('world-first-hall',18000),('world-first-hall',60),('world-hall-batch',7200),('world-west',3600),('town02',60)]:
            self.assertIsNone(validate_recording_budget(prefix,budget))
        for prefix,budget in [('world-first-hall',18001),('world-first-hall',59),('world-first-hall',10**9),('world-west',7200),('town02',3601),('world-hall-batch',7201),('world-hall-batch',59)]:
            with self.assertRaises(ValueError):validate_recording_budget(prefix,budget)

    def test_runtime_recording_commands_fit_their_scoped_budget(self):
        import re
        script=(Path(__file__).resolve().parents[1]/'ci/run-town02-runtime.sh').read_text(encoding='utf-8')
        commands=re.findall(r'^python tools/record_app_audio.py ([\w-]+) .*?--budget-seconds (\d+)$',script,re.M)
        self.assertGreater(len(commands),10)
        self.assertIn(('world-first-hall','18000'),commands)
        for prefix,budget in commands:
            with self.subTest(prefix=prefix,budget=budget):
                validate_recording_budget(prefix,int(budget))

    def test_atomic_replacement_returns_only_the_actual_complete_save(self):
        state={'mapId':96,'x':248,'characters':[{'id':'nezha','hp':51}],'flags':{'won':True}}
        xml=('<map><string name="saveJson">'+json.dumps(state)+'</string></map>').encode()
        reads=iter([b'',b'<map>',None,xml]);pauses=[]
        self.assertEqual(state,read_saved_boundary(lambda _:next(reads),pause=pauses.append))
        self.assertEqual([.1,.1,.1],pauses)
    def test_persistently_missing_or_malformed_boundary_is_never_accepted(self):
        for raw in [None,b'',b'<map>',b'<map/>',b'<map><string name="saveJson">bad</string></map>',
                    b'<map><string name="saveJson">[]</string></map>']:
            reads=[]
            def read(_):reads.append(1);return raw
            with self.assertRaises(SavedBoundaryUnavailable):read_saved_boundary(read,pause=lambda _:None,attempts=3)
            self.assertEqual(3,len(reads))
    def test_valid_boundary_needs_no_delay_and_transport_error_is_not_hidden(self):
        pauses=[]
        self.assertEqual({'mapId':60},read_saved_boundary(lambda _:b'<map><string name="saveJson">{"mapId":60}</string></map>',pause=pauses.append))
        self.assertEqual([],pauses)
        def denied(_):raise PermissionError('fixture denied')
        with self.assertRaises(PermissionError):read_saved_boundary(denied,pause=lambda _:None)
if __name__=='__main__':unittest.main()
