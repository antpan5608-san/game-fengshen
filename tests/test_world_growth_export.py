"""Full target-owner growth preserves the witnessed subset and rejects wrong ownership/caps."""
import copy,json,os,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
from export_development import iteration_reader,extend_world_growth

class WorldGrowthTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=iteration_reader()
        apk=Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK',str(ci.ROOT/'artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')))
        cls.old=json.loads(ci.content(apk,ci.CONFIG['iteration']['base'])['combat.json'])
        cls.proof=json.loads((ci.ROOT/'game-data/provenance/world-growth.json').read_text(encoding='utf-8'))['growthExtension']
    def test_complete_owner_table_preserves_observed_rows_and_exact_next_threshold(self):
        c=copy.deepcopy(self.old);extend_world_growth(self.reader,c,self.proof,'game-data/provenance/world-growth.json')
        self.assertEqual(list(range(2,81)),[r['level'] for r in c['nezhaGrowth']])
        self.assertEqual(self.old['nezhaGrowth'],c['nezhaGrowth'][:len(self.old['nezhaGrowth'])])
        level11=next(r for r in c['nezhaGrowth'] if r['level']==11)
        self.assertEqual((1860,14,3,1,1,1),(level11['threshold'],level11['hp'],level11['strength'],level11['stamina'],level11['agility'],level11['spirit']))
        self.assertFalse(level11['runtimeVerified']);self.assertEqual(6455932,c['nezhaGrowth'][-1]['threshold'])
        self.assertEqual(80,c['growthLimit']['level']);self.assertEqual('nezha',c['growthLimit']['owner'])
    def test_wrong_owner_cap_or_table_cannot_replace_the_game_table(self):
        for key,value in [('actorIndex',1),('maxLevel',81),('levels',[1,80])]:
            proof=copy.deepcopy(self.proof);proof[key]=value
            with self.assertRaises(ValueError):extend_world_growth(self.reader,copy.deepcopy(self.old),proof,'fixture')
        proof=copy.deepcopy(self.proof);proof['levelCapSource']['offset']+=1
        with self.assertRaises(ValueError):extend_world_growth(self.reader,copy.deepcopy(self.old),proof,'fixture')
    def test_existing_verified_threshold_cannot_be_silently_corrected(self):
        c=copy.deepcopy(self.old);c['nezhaGrowth'][0]['threshold']=13
        with self.assertRaisesRegex(ValueError,'witnessed'):extend_world_growth(self.reader,c,self.proof,'fixture')

if __name__=='__main__':unittest.main()
