"""Current shared village batch from the original fixed APK; not Android acceptance."""
import copy,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex

class WestVillageExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=ex.load(ROOT/'ci/golden-world-west-villages-content.json')
        cls.basePath=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
        cls.base=ci.content(cls.basePath,cls.pin['iteration']['base'])
        cls.parent=ex.load(ROOT/'ci/golden-world-jiameng-content.json')
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.out=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin)
        cls.scene=json.loads(cls.out['scene.json']);cls.proofPath=ROOT/'game-data/provenance/world-village-batch-resources.json'
    def test_exact_target_preserves_prior_media_and_real_return(self):
        self.assertEqual(64,len(self.scene['maps']))
        self.assertEqual(self.pin['manifestSha256'],ex.digest(self.out['manifest.json']))
        for name,data in self.old.items():
            if not name.endswith('.json'):self.assertEqual(data,self.out[name],name)
        entrances=[e for e in self.scene['exits']if e['fromMapId']==16 and e['toMapId']==9]
        self.assertEqual([[39,81],[40,81]],[e['trigger']for e in entrances])
        back=next(e for e in self.scene['exits']if e['fromMapId']==9 and e['toMapId']==16)
        self.assertEqual([40,81],back['spawn']);self.assertTrue(back['preserveArrivalDirection'])
    def test_three_callers_real_stock_and_21_actors_no_fake_cure(self):
        from forensics.fengshen246 import extract_world_service_catalog
        stock=extract_world_service_catalog(ex.iteration_reader())
        ns=[n for n in self.scene['npcs']if n['mapId']in (8,9,10)];self.assertEqual(21,len(ns))
        self.assertEqual(7,len([n for n in ns if n.get('hiddenInvestigation')]))
        for mid in (8,9,10):
            ex.validate_world_village_batch_resources(ex.iteration_reader(),mid)
            self.assertEqual(6,len([b for b in self.scene['serviceBindings']if b['callerMapId']==mid]))
            for room,category in [(17,'weapon'),(18,'armor'),(19,'medicine')]:
                definition=next(s for s in self.scene['shops']if s['id']==f'rom.shop.{mid}.{room}')
                original=next(s for s in stock['stocks']if s['category']==category and s['contextIndex']==mid)
                self.assertEqual(original['originalIds'],[int(i.split('.')[-1])for i in definition['items']])
        special=next(i for i in self.scene['items']if i['id']=='rom.special.14')
        self.assertNotIn('worldUse',special);self.assertNotIn('buyPrice',special)
        self.assertEqual(1,next(n for n in ns if n['id']=='rom.npc.9.1')['moneyTreasure']['amount'])
        self.assertTrue(next(n for n in ns if n['id']=='rom.npc.10.6')['talkDisabled'])
        self.assertTrue(all(not n['firstEffects']for n in ns))
    def test_existing_restore_rebuilds_from_empty_directory(self):
        with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
            out=Path(td)/'content';ci.restore(self.basePath,out,next_code=84)
            self.assertEqual(self.out,{p.name:p.read_bytes()for p in out.iterdir()})
    def test_altered_grant_witness_font_and_actor_source_reject(self):
        original=ex.load
        for mode in ('amount','witness','glyph','actor'):
            p=copy.deepcopy(original(self.proofPath));v=next(v for v in p['villages']if v['mapId']==9)
            if mode=='amount':v['npcs'][1]['moneyTreasure']['amount']=650
            elif mode=='witness':v['npcs'][0]['originalTalk']['witnessFlagId']='invented.condition'
            elif mode=='glyph':v['font']['glyphs'][0]['pixelsSha256']='0'*64
            else:v['graphics'][v['npcs'][0]['sprite']]['poseMapId']=1
            with patch.object(ex,'load',side_effect=lambda f:p if Path(f)==self.proofPath else original(f)),self.assertRaises(ValueError):
                ex.validate_world_village_batch_resources(ex.iteration_reader(),9)

if __name__=='__main__':unittest.main()
