import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class FieldProtectionEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader()
        cls.proof=json.loads((ex.ROOT/'game-data/provenance/world-field67-item12.json').read_text(encoding='utf-8'))
    def item(self):
        return {'id':'rom.special.12','category':'special','originalId':12,'name':'定神珠','maxCount':1,
            'fieldProtectionUse':{'mapId':67,'reusable':True,'evidence':'game-data/provenance/world-field67-item12.json'},
            'source':{'nameRange':self.r.span(2,0xe847,5,'Actual name pointer source')}}
    def test_actual_cpu_acceptance_consumption_and_menu_transition_evidence(self):
        ex.validate_world_field_protection_item(self.r,self.item())
        raw=(ex.ROOT/self.proof['cpuExpectedPath']).read_text(encoding='utf-8')
        rows=[list(map(int,l.split('\t')))for l in raw.splitlines()]
        self.assertEqual(48,len(rows))
        for mid,pending,qty,selectable,accepted,after,quantity in rows:
            self.assertEqual(int(bool(qty&127)),selectable)
            self.assertEqual(int(mid==67 and bool(qty&127)),accepted)
            self.assertEqual(1 if accepted else pending,after)
            self.assertEqual(qty|128 if accepted else qty,quantity)
        boundaries=self.proof['controlledMenuBoundaries']
        self.assertEqual([67,67,67,67,23,23,67],[a['mapId']for a in boundaries])
        self.assertEqual([0,0,1,1,0,0,0],[a['pending6814']for a in boundaries])
        self.assertEqual([0,0,0,1,1,1,1],[a['active686f']for a in boundaries])
        self.assertEqual({(35,29)},{tuple(a['partyHp'])for a in boundaries})
    def test_wrong_item_use_price_effect_or_protection_rule_is_rejected(self):
        for key,value in [('originalId',11),('name','辟火罩'),('buyPrice',100),('worldUse',{}),('maxCount',10)]:
            item=self.item();item[key]=value
            with self.assertRaises(ValueError):ex.validate_world_field_protection_item(self.r,item)
        for key in ['mapReconstructionClearsPendingOnly','poisonNotPrevented','selectionOnly']:
            proof=copy.deepcopy(self.proof);proof['rules'][key]=False
            with patch.object(ex,'load',return_value=proof),self.assertRaises(ValueError):ex.validate_world_field_protection_item(self.r,self.item())

if __name__=='__main__':unittest.main()
