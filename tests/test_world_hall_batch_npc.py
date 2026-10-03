"""Original NPC recipes retain raw identity and a complete visible pose."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class HallBatchNpcTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader();cls.proof=json.loads((ex.ROOT/'game-data/provenance/world-hell-hall-batch-resources.json').read_text(encoding='utf-8'))
    def test_each_actor_and_companion_rebuilds_its_own_nonempty_pixels(self):
        self.assertEqual(10,len(self.proof['npcSprites']))
        for sid in self.proof['npcSprites']:
            recipe=ex.validate_world_hall_batch_npc_graphic(self.r,int(sid))
            self.assertEqual(4,len(recipe['tiles']));self.assertGreater(recipe['opaquePixelCount'],0);self.assertFalse(recipe['normalPlayEvidence'])
    def test_transparent_unfinished_pose_cannot_be_promoted(self):
        with self.assertRaises(ValueError):ex.validate_world_hall_batch_npc_graphic(self.r,198)
        self.assertEqual('TRANSPARENT_POSE_NOT_COMPLETE_GRAPHIC',self.proof['unresolvedNpcSprites']['198']['status'])
    def test_wrong_binding_pixels_scope_or_empty_pose_is_rejected(self):
        for case in ['id','binding','pixels','normal','count']:
            p=copy.deepcopy(self.proof);n=p['npcSprites']['192']
            if case=='id':n['spriteId']=193
            elif case=='binding':n['bindings'][0]['npcIndex']=0
            elif case=='pixels':n['recipe']['rgbaSha256']='0'*64
            elif case=='normal':n['recipe']['normalPlayEvidence']=True
            else:n['recipe']['opaquePixelCount']=0
            with patch.object(ex,'load',return_value=p),self.assertRaises(ValueError,msg=case):ex.validate_world_hall_batch_npc_graphic(self.r,192)
if __name__=='__main__':unittest.main()
