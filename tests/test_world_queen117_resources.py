"""Actual scoped Queen sources; no normal route or Android completion claim."""
import copy, json, sys, unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class QueenResourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path='game-data/provenance/world-queen117-state.json'
        cls.proof=json.loads((ex.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.reader=ex.iteration_reader()
    def changed(self,proof):
        original=ex.load
        def load(path):return proof if Path(path).resolve()==(ex.ROOT/self.path).resolve()else original(path)
        with patch.object(ex,'load',side_effect=load):return ex.validate_world_queen117_resources(self.reader)
    def test_actual_original_trigger_identity_stats_and_local_completion(self):
        p=ex.validate_world_queen117_resources(self.reader)
        self.assertEqual([7,5],p['rules']['triggerCell']);self.assertEqual(18,p['rules']['scriptId'])
        self.assertEqual((7000,372,178,3000,2400),tuple(p['enemy'][f]for f in ['hp','attack','defense','experienceReward','moneyReward']))
        self.assertEqual(0,p['boss']['intro']['completedSteps'])
        self.assertTrue(p['boss']['commitAfterDialogue']);self.assertFalse(p['rules']['extraEventReward'])
        self.assertEqual([1536,1024,36],[t['caseCount']for t in p['cpu']])
        self.assertEqual(2,p['itemCapabilityUpdates'][0]['fields']['battleBindingUse']['bindingMarker'])
    def test_actual_stable_graphic_is_rebuilt_from_all_original_tiles(self):
        g=self.proof['queenGraphic'];self.assertEqual([96,32,80,96],g['observedRect']);self.assertEqual(120,len(g['tiles']))
        self.assertEqual(ex.scoped_observed_graphic(self.reader,g),ex.scoped_observed_graphic(self.reader,g))
        for kind in ['rgba','tile','missing']:
            bad=copy.deepcopy(g)
            if kind=='rgba':bad['rgbaSha256']='0'*64
            elif kind=='tile':bad['tiles'][12]['sha256']='0'*64
            else:bad['tiles'].pop()
            with self.assertRaises(ValueError,msg=kind):ex.scoped_observed_graphic(self.reader,bad)
    def test_guessed_effect_trigger_script_reward_and_transient_graphic_rejected(self):
        for case in ['hp','trigger','script','reward','flags','marker','consume','name','font','graphic']:
            p=copy.deepcopy(self.proof)
            if case=='hp':p['enemy']['hp']=1
            elif case=='trigger':p['rules']['triggerCell']=[7,3]
            elif case=='script':p['rules']['scriptId']=19
            elif case=='reward':p['rules']['extraEventReward']=True
            elif case=='flags':p['boss']['continuation']['completionFlags'].pop()
            elif case=='marker':p['itemCapabilityUpdates'][0]['fields']['battleBindingUse']['bindingMarker']=1
            elif case=='consume':p['itemCapabilityUpdates'][0]['fields']['battleBindingUse']['reusable']=False
            elif case=='name':p['dialogues'][2]['text']='女王死后，获得金币9999。'
            elif case=='font':p['font']['glyphs'][0]['character']='假'
            else:p['queenGraphic']['observedRect']=[96,32,72,96]
            with self.assertRaises(ValueError,msg=case):self.changed(p)

if __name__=='__main__':unittest.main()
