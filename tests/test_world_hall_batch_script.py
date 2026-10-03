"""Original finalization/door data, not full-HP victory or normal App evidence."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class HallBatchScriptTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader();cls.p=json.loads((ex.ROOT/'game-data/provenance/world-hell-hall-batch-script.json').read_text(encoding='utf-8'))
        cls.rows=[list(map(int,l.split('\t')))for l in (ex.ROOT/cls.p['cpuExpectedPath']).read_text(encoding='utf-8').splitlines()if l and not l.startswith('#')]
    def test_each_actual_event_identity_uses_its_own_flag_mask_and_repeat(self):
        self.assertEqual(4096,len(self.rows));rules={mid:ex.validate_world_hall_batch_script(self.r,mid)for mid in range(61,69)}
        self.assertEqual([4,8,8,8,8,16,16,2],[r['gateMask']for r in rules.values()])
        self.assertEqual([1,1,1,2,1,1,1,1],[r['eventArgument']for r in rules.values()])
        for mid,before,success,after,present,message,stage in self.rows:
            r=rules[mid];self.assertEqual((before|r['gateMask']|r['eventArgument'])if success else before,after)
            self.assertEqual(int(not bool(after&r['gateMask'])),present)
            self.assertEqual(r['repeatMessage']if success else r['firstMessage'],message);self.assertEqual(2 if success else 0,stage)
    def test_wrong_gate_source_target_event_or_common_instruction_refused(self):
        for case in ['gate','source','event','npc','repeat','cell','evidence']:
            p=copy.deepcopy(self.p);r=p['maps']['62']
            if case=='gate':r['gateMask']=4
            elif case=='source':r['sourceType']=160
            elif case=='event':r['eventArgument']=2
            elif case=='npc':r['npcIndex']=2
            elif case=='repeat':r['repeatMessage']=4
            elif case=='cell':r['barrierCell']=[17,2]
            else:p['sources'].pop()
            with patch.object(ex,'load',return_value=p),self.assertRaises(ValueError,msg=case):ex.validate_world_hall_batch_script(self.r,62)
    def test_no_unproven_map_or_normal_victory_promotion(self):
        for mid in [23,60,69,70,175]:
            with self.assertRaises(ValueError):ex.validate_world_hall_batch_script(self.r,mid)
        self.assertEqual('NOT_RUN',self.p['normalAndroid']);self.assertIn('NOT_NORMAL',self.p['kind'])
if __name__=='__main__':unittest.main()
