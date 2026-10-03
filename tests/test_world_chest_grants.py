"""Real chest identity/grant evidence; no medicine or equipment effect inferred."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class ChestGrantTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader();cls.p=json.loads((ex.ROOT/'game-data/provenance/world-hell-chest-grants.json').read_text(encoding='utf-8'))
    def npc(self,b):
        cat={0:'medicine',1:'special',2:'weapon',3:'armor'}[b['categoryId']]
        stable='rom.item.0'if(b['categoryId'],b['originalId'])==(2,0)else f'rom.{cat}.{b["originalId"]}'
        return {'id':f'rom.npc.{b["mapId"]}.{b["npcIndex"]}','mapId':b['mapId'],'openedSprite':'chest-144-open.png',
            'source':{'record':b['source']},'treasure':{'itemId':stable,'flagId':f'rom.map.{b["mapId"]}.flag.{b["flagMask"]}',
                'amount':1,'categoryGrant':b['categoryId'],'evidence':'game-data/provenance/world-hell-chest-grants.json'}}
    def test_every_actual_chest_and_capacity_table_is_preserved(self):
        self.assertEqual(9,len(self.p['bindings']))
        for b in self.p['bindings']:self.assertEqual(b,ex.validate_world_chest_grant(self.r,self.npc(b)))
        self.assertEqual('NOT_RUN',self.p['normalAndroid']);self.assertFalse(self.p['rules']['worldUseRuleInferred'])
    def test_wrong_reward_category_flag_or_closed_graphic_is_rejected(self):
        for field,val in [('amount',2),('categoryGrant',2),('itemId','rom.medicine.0'),('flagId','rom.map.61.flag.4')]:
            n=self.npc(self.p['bindings'][0]);n['treasure'][field]=val
            with self.assertRaises(ValueError,msg=field):ex.validate_world_chest_grant(self.r,n)
        n=self.npc(self.p['bindings'][0]);n['openedSprite']=None
        with self.assertRaises(ValueError):ex.validate_world_chest_grant(self.r,n)
    def test_missing_grant_capacity_or_flag_source_cannot_be_promoted(self):
        for case in ['sources','capacity','worldEffect']:
            p=copy.deepcopy(self.p)
            if case=='sources':p['sources'].pop()
            elif case=='capacity':p['rules']['categoryLimits'][0]=99
            else:p['rules']['worldUseRuleInferred']=True
            with patch.object(ex,'load',return_value=p),self.assertRaises(ValueError):ex.validate_world_chest_grant(self.r,self.npc(self.p['bindings'][0]))
if __name__=='__main__':unittest.main()
