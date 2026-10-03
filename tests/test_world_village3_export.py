"""Pinned checkpoint reuse and original post-rebirth village; not normal App acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_map,extract_world_service_catalog

class Village3ExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-village3-content.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-village3-content.json';cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.scene=json.loads(cls.result['scene.json']);cls.r=ex.iteration_reader()
    def changed(self,p,pin=None):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,pin or self.pin,verify_target=False)
    def test_pinned_local_checkpoint_restores_without_previous_candidate_apk_or_changed_media(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(188,len(self.result));self.assertEqual(32,len(self.scene['maps']))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
        ci.validate_item_sources(self.result)
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        village=json.loads(self.result['scene3.json']);m=extract_map(self.r,3)
        self.assertEqual([t for row in m['grid']for t in row],village['grid']);self.assertEqual((32,30),(village['width'],village['height']))
        self.assertTrue(village['sourceEdges']);self.assertEqual(10,len([n for n in self.scene['npcs']if n['mapId']==3]))
    def test_actual_stock_lodging_slot_membership_and_independent_return(self):
        stocks={(s['category'],s['contextIndex']):s for s in extract_world_service_catalog(self.r)['stocks']}
        for room,category in [(17,'weapon'),(18,'armor'),(19,'medicine')]:
            shop=next(s for s in self.scene['shops']if s['id']==f'rom.shop.3.{room}')
            self.assertEqual([f'rom.{category}.{i}'for i in stocks[category,3]['originalIds']],shop['items'])
        self.assertNotIn('rom.medicine.0',next(s for s in self.scene['shops']if s['id']=='rom.shop.3.19')['items'])
        inn=next(i for i in self.scene['inns']if i['id']=='rom.inn.3');self.assertEqual((40,114),(inn['price'],inn['blockedStatusMask']))
        exit=next(e for e in self.scene['exits']if e['fromMapId']==3 and e['toMapId']==16)
        self.assertEqual(([15,29],[239,160],'EDGE','DOWN'),(exit['trigger'],exit['spawn'],exit['triggerMode'],exit['direction']))
        self.assertEqual([255,29,16,239,160],list(ex.checked_span(self.r,exit['source'])))
        items={i['id']:i for i in self.scene['items']}
        self.assertEqual(['xiaolongnv'],items['rom.weapon.21']['equipment']['allowedCharacters'])
        self.assertEqual(['nezha'],items['rom.armor.30']['equipment']['allowedCharacters'])
        foot=items['rom.armor.30']['equipment']
        foot_list=next(s for s in foot['ruleSources']if s['cpuAddress']==0xefa6)
        self.assertIn(30,ex.checked_span(self.r,foot_list)[:-1])
        self.assertEqual(8,foot['evasionValue'])
        self.assertNotIn(0xec0f,[s['cpuAddress']for s in foot['ruleSources']])
        self.assertNotIn('equipment',items['rom.weapon.33'])
        self.assertFalse(any('herbUse'in items[k]for k in ['rom.medicine.10','rom.medicine.14']))
    def test_forged_checkpoint_pin_provenance_base_or_cycle_is_rejected(self):
        for kind in ['pin','proof','base-hash','different-apk']:
            p=copy.deepcopy(self.p);pin=copy.deepcopy(self.pin)
            if kind=='pin':p['baseExport']['pinSha256']='0'*64
            elif kind=='proof':p['baseExport']['provenanceSha256']='0'*64
            elif kind=='base-hash':p['baseManifestSha256']='0'*64
            else:pin['iteration']['base']['apkSha256']='0'*64
            with self.assertRaises(ValueError,msg=kind):self.changed(p,pin)
        with self.assertRaisesRegex(ValueError,'Cyclic'):
            ex.export_from_base(self.base,self.path,self.pin,_visited=frozenset({(ci.ROOT/self.path).resolve()}))
    def test_observed_horizontal_flip_is_not_optional_and_wrong_price_stock_or_owner_rejected(self):
        recipe=copy.deepcopy(self.p['graphics']['npc-164-village3.png'])
        self.assertTrue(any(t.get('flipX')for t in recipe['tiles']))
        for t in recipe['tiles']:t['flipX']=False
        with self.assertRaisesRegex(ValueError,'RGBA|OAM flip'):ex.scoped_observed_graphic(self.r,recipe)
        recipe['tiles'][0]['flipX']='false'
        with self.assertRaisesRegex(ValueError,'boolean'):ex.scoped_observed_graphic(self.r,recipe)
        for kind in ['stock','cost','owner','return']:
            p=copy.deepcopy(self.p)
            if kind=='stock':p['shops'][0]['items'].pop()
            elif kind=='cost':p['inns'][0]['price']=0
            elif kind=='owner':next(i for i in p['items']if i['id']=='rom.weapon.21')['equipment']['allowedCharacters']=['nezha']
            else:next(e for e in p['exits']if e['kind']=='EDGE_RECORD')['spawn'][1]=29
            with self.assertRaises(ValueError,msg=kind):self.changed(p)

if __name__=='__main__':unittest.main()
