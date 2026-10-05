"""Two exact original house callers; content checks do not prove Android play."""
import copy,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex

class WestHouseExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=ex.load(ROOT/'ci/golden-world-west-houses-content.json')
        cls.basePath=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
        cls.base=ci.content(cls.basePath,cls.pin['iteration']['base'])
        cls.parent=ex.load(ROOT/'ci/golden-world-west-villages-content.json')
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.out=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin)
        cls.scene=json.loads(cls.out['scene.json'])
    def test_house_table_entrances_and_independent_returns_preserve_old_media(self):
        self.assertEqual(66,len(self.scene['maps']))
        self.assertEqual(self.pin['manifestSha256'],ex.digest(self.out['manifest.json']))
        for name,data in self.old.items():
            if not name.endswith('.json'):self.assertEqual(data,self.out[name],name)
        for mid,door,spawn in [(41,[25,7],[5,12]),(42,[13,4],[7,12])]:
            entry=next(e for e in self.scene['exits']if e['fromMapId']==10 and e['toMapId']==mid)
            back=next(e for e in self.scene['exits']if e['fromMapId']==mid)
            self.assertEqual((door,spawn,True),(entry['trigger'],entry['spawn'],entry['captureCaller']))
            self.assertEqual((spawn,door,True),(back['trigger'],back['spawn'],back['returnToCaller']))
            ns=[n for n in self.scene['npcs']if n['mapId']==mid]
            self.assertEqual(1 if mid==41 else 3,len(ns));self.assertTrue(all(not n['firstEffects']for n in ns))
        item=next(i for i in self.scene['items']if i['id']=='rom.special.14')
        self.assertNotIn('worldUse',item) # Native use evidence is not a completed runtime implementation.
    def test_existing_restore_rebuilds_exact_target_in_empty_directory(self):
        with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
            out=Path(td)/'content';ci.restore(self.basePath,out,next_code=84)
            self.assertEqual(self.out,{p.name:p.read_bytes()for p in out.iterdir()})
    def test_tampered_house_target_actor_font_and_return_reject(self):
        original=ex.load;proofPath=ROOT/'game-data/provenance/world-west-houses-resources.json'
        for mode in ('table','actor','font'):
            proof=copy.deepcopy(original(proofPath));room=proof['rooms'][0]
            if mode=='table':proof['bindings'][0]['spawn']=[7,12]
            elif mode=='actor':room['npcs'][0]['firstEffects']=[{'money':1}]
            else:room['font']['glyphs'][0]['pixelsSha256']='0'*64
            with patch.object(ex,'load',side_effect=lambda p:proof if Path(p)==proofPath else original(p)),self.assertRaises(ValueError):
                ex.validate_world_house_resources(ex.iteration_reader(),41)
        recipePath=ROOT/self.pin['iteration']['provenance'];recipe=copy.deepcopy(original(recipePath))
        next(e for e in recipe['exits']if e['fromMapId']==41)['spawn']=[13,4]
        with patch.object(ex,'load',side_effect=lambda p:recipe if Path(p)==recipePath else original(p)),self.assertRaises(ValueError):
            ex.export_from_base(self.base,self.pin['iteration']['provenance'],self.pin)

if __name__=='__main__':unittest.main()
