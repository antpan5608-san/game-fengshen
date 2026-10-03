"""ROM-backed scoped dynamic-chunk compilation, not normal App evidence."""
import copy
import unittest
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex

class SceneMechanismExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=ex.iteration_reader()
        cls.rule=dict(id='rom.mechanism.95.0',mapId=95,x=12,y=21,
            sessionFlag='runtime.session.map95.mechanism0',evidence='game-data/provenance/world-east-mechanism.json')
    def run_export(self,rule=None,scene=None):
        scene=scene if scene is not None else {'maps':[{'id':95}]}
        ex.extend_world_mechanisms(self.reader,scene,[rule or self.rule],'test')
        return scene
    def test_original_chunks_derive_exact_nine_changed_cells(self):
        row=self.run_export()['mechanisms'][0]
        self.assertEqual(9,len(row['changes']))
        self.assertEqual({(x,y) for y in range(17,20) for x in range(12,15)}, {(c['x'],c['y']) for c in row['changes']})
        self.assertTrue(all((c['toTile'],c['fromCollision'],c['toCollision'])==(102,1,0) for c in row['changes']))
        self.assertEqual('ANDROID_SESSION_NOT_ORIGINAL_MANUAL_SAVE',row['resumePolicy'])
    def test_rejects_other_trigger_and_permanent_flag(self):
        for field,value in [('id','other'),('mapId',94),('x',11),('y',20),('sessionFlag','rom.map.95.flag.128')]:
            with self.subTest(field=field),self.assertRaises(ValueError):
                self.run_export(dict(self.rule,**{field:value}))
    def test_requires_loaded_scene_and_unique_mechanism(self):
        with self.assertRaises(ValueError):self.run_export(scene={'maps':[{'id':94}]})
        scene=self.run_export()
        with self.assertRaises(ValueError):self.run_export(scene=scene)
    def test_deterministic_and_does_not_change_input_definition(self):
        before=copy.deepcopy(self.rule)
        self.assertEqual(self.run_export(),self.run_export())
        self.assertEqual(before,self.rule)

if __name__=='__main__':unittest.main()
