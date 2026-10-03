"""Strict read-only evidence boundaries across real XML replacement failure modes."""
import json,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from record_app_audio import read_saved_boundary,SavedBoundaryUnavailable,validate_recording_budget

class RecordingBoundaryTests(unittest.TestCase):
    def test_only_scoped_first_preparation_and_hall_batch_have_a_longer_bounded_budget(self):
        for prefix,budget in [('world-first-hall',7200),('world-hall-batch',7200),('world-west',3600),('town02',60)]:
            self.assertIsNone(validate_recording_budget(prefix,budget))
        for prefix,budget in [('world-first-hall',7201),('world-west',7200),('town02',3601),('world-hall-batch',7201),('world-hall-batch',59)]:
            with self.assertRaises(ValueError):validate_recording_budget(prefix,budget)

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
