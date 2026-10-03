"""Original planes/encounter dispatch; not full route or field-hazard evidence."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class HallBatchTerrainTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader();cls.proof=json.loads((ex.ROOT/'game-data/provenance/world-hell-hall-batch-terrain.json').read_text(encoding='utf-8'))
    def test_actual_grid_and_full_two_plane_scope_remain_separate(self):
        rules=[ex.validate_world_hall_batch_terrain(self.r,mid)for mid in range(61,69)]
        self.assertEqual(6840,sum(r['testCount']for r in rules))
        self.assertIn('17',rules[2]['classCounts']);self.assertIn('18',rules[2]['classCounts'])
        self.assertEqual('NOT_RUN',self.proof['normalAndroid']);self.assertIn('NOT_NORMAL',self.proof['kind'])
    def test_occlusion_bytes_do_not_modify_actual_encounter_branch(self):
        gate=self.proof['encounterGate'];rows=[list(map(int,l.split('\t')))for l in (ex.ROOT/gate['cpuExpectedPath']).read_text(encoding='utf-8').splitlines()if l and not l.startswith('#')]
        self.assertEqual(480,len(rows));outcomes={}
        for mid,occlusion,steps,rng,trigger in rows:
            kind=self.r.read(11,0xed87+mid)[0]
            self.assertEqual(int(steps>=50 or (rng<16 if kind>=11 else rng>=245)),trigger)
            outcomes.setdefault((mid,steps,rng),set()).add(trigger)
        self.assertTrue(all(len(v)==1 for v in outcomes.values()));self.assertEqual({0,1,255},{r[1]for r in rows})
    def test_unknown_map_wrong_grid_mode_encounter_or_source_is_refused(self):
        for mid in [23,60,69,70,175]:
            with self.assertRaises(ValueError):ex.validate_world_hall_batch_terrain(self.r,mid)
        for case in ['grid','classes','mode','encounter','source','upper']:
            p=copy.deepcopy(self.proof)
            if case=='grid':p['maps']['63']['gridSha256']='0'*64
            elif case=='classes':p['maps']['63']['classCounts']['0']+=1
            elif case=='mode':p['inputModes']=[0]
            elif case=='encounter':p['encounterGate']['suppressesOn9b']=True
            elif case=='source':p['sources'].pop()
            else:p['rules']['upperStandingClasses'].append(20)
            with patch.object(ex,'load',return_value=p),self.assertRaises(ValueError,msg=case):ex.validate_world_hall_batch_terrain(self.r,63)
if __name__=='__main__':unittest.main()
