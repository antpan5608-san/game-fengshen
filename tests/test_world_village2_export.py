"""Real scoped service stock, caller return and explicit old price completion."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter
from forensics.fengshen246 import extract_map,extract_world_service_catalog

class Village2ExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-village2-content.json').read_text(encoding='utf-8'))
        cls.proof=cls.pin['iteration']['provenance'];cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin);cls.scene=json.loads(cls.result['scene.json'])
    def test_independent_normal_directional_connections_and_shared_caller_doors(self):
        exits=self.scene['exits'];expected={(23,(55,91),2,(30,19)),(2,(30,19),23,(55,91))}
        self.assertTrue(expected<={(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in exits})
        for e in exits:
            if e['fromMapId']==2 and e['toMapId'] in [17,18,19,22]:self.assertTrue(e['captureCaller'])
        actual={(e['toMapId'],tuple(e['trigger']),tuple(e['spawn']))for e in exits if e['fromMapId']==2 and e['toMapId']!=23}
        self.assertEqual({(17,(6,20),(7,12)),(18,(10,19),(7,12)),(19,(6,26),(6,12)),(22,(6,14),(12,14))},actual)
        bindings=[b for b in self.scene['serviceBindings']if b['callerMapId']==2];self.assertEqual(4,len(bindings))
        self.assertEqual({17,18,19,22},{b['interiorMapId']for b in bindings})
    def test_full_original_stock_price_and_uninvented_medicine_effects(self):
        reader=exporter.iteration_reader();catalog=extract_world_service_catalog(reader);items={i['id']:i for i in self.scene['items']}
        for shop in [s for s in self.scene['shops']if s['id'].startswith('rom.shop.2.')]:
            cat=shop['source']['category'];source=next(s for s in catalog['stocks']if(s['category'],s['contextIndex'])==(cat,2))
            self.assertEqual([f'rom.{cat}.{i}'for i in source['originalIds']],shop['items']);self.assertEqual(shop['items'],shop['sellItems'])
            for iid in shop['items']:
                original=next(i for i in catalog['items']if i['id']==iid);self.assertEqual((original['buyPrice'],original['sellPrice']),(items[iid]['buyPrice'],items[iid]['sellPrice']))
        for iid in ['rom.medicine.4','rom.medicine.7']:
            for key in ['herbUse','antidoteUse','worldUse']:self.assertNotIn(key,items[iid])
    def test_old_loot_identity_and_unknown_use_preserved_while_absent_prices_are_completed(self):
        old=next(i for i in json.loads(self.base['scene.json'])['items']if i['id']=='rom.medicine.7')
        current=next(i for i in self.scene['items']if i['id']==old['id']);copy_current=copy.deepcopy(current)
        self.assertEqual((15,7),(current['buyPrice'],current['sellPrice']))
        copy_current.pop('buyPrice');copy_current.pop('sellPrice');copy_current['source'].pop('merchantPriceEvidence');copy_current['source'].pop('merchantPriceRange')
        self.assertEqual(old,copy_current)
        legacy=json.loads((ci.ROOT/'game-data/provenance/world-hell-encounters-content.json').read_text())
        verified=next(i for i in legacy['items']if i['id']=='rom.medicine.12')
        self.assertEqual(verified,next(i for i in self.scene['items']if i['id']=='rom.medicine.12'))
        self.assertEqual(len(self.scene['items']),len({i['id']for i in self.scene['items']}))
    def test_optional_inn_and_actor_specific_equipment_do_not_modify_initial_party_or_quest(self):
        inn=next(i for i in self.scene['inns']if i['id']=='rom.inn.2');self.assertEqual((20,114),(inn['price'],inn['blockedStatusMask']))
        items={i['id']:i for i in self.scene['items']}
        self.assertEqual((['xiaolongnv'],'rightHand',12),(items['rom.weapon.18']['equipment']['allowedCharacters'],items['rom.weapon.18']['equipment']['slot'],items['rom.weapon.18']['equipment']['attackBonus']))
        self.assertEqual((['xiaolongnv'],'body',12),(items['rom.armor.10']['equipment']['allowedCharacters'],items['rom.armor.10']['equipment']['slot'],items['rom.armor.10']['equipment']['defenseBonus']))
        self.assertEqual(['nezha'],items['rom.weapon.4']['equipment']['allowedCharacters']);self.assertEqual(25,items['rom.weapon.4']['equipment']['attackBonus'])
        self.assertFalse(any(n.get('mapId')==2 and n.get('firstEffects')for n in self.scene['npcs']))
    def test_repeatable_map_preserves_previous_media_and_original_unresolved_features(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        golden=json.loads((ci.ROOT/'ci/golden-world-hell-encounters-content.json').read_text(encoding='utf-8'));old=exporter.export_from_base(self.base,golden['iteration']['provenance'],golden)
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        village=json.loads(self.result['scene2.json']);original=extract_map(exporter.iteration_reader(),2)
        self.assertEqual([t for row in original['grid']for t in row],village['grid']);self.assertEqual((32,30),(village['width'],village['height']))
        self.assertTrue(village['sourceEdges']);self.assertTrue(any('clinic' in s for s in village['limitations']))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
    def test_wrong_price_pointer_hash_stock_cost_or_owner_is_rejected(self):
        for kind in ['price','price-source','old-hash','stock','inn-price','owner','slot','duplicate-item']:
            evidence=copy.deepcopy(self.evidence);update=evidence['existingItemPriceUpdates'][0]
            if kind=='price':update['buyPrice']+=1
            elif kind=='price-source':update['priceSource']=next(i for i in evidence['items']if i['id']=='rom.medicine.4')['source']['priceRange']
            elif kind=='old-hash':update['baseDefinitionSha256']='0'*64
            elif kind=='stock':next(s for s in evidence['shops']if s['id']=='rom.shop.2.19')['items'].pop()
            elif kind=='inn-price':next(i for i in evidence['inns']if i['id']=='rom.inn.2')['price']=0
            elif kind=='duplicate-item':evidence['items'].append(copy.deepcopy(evidence['items'][0]))
            elif kind=='owner':next(i for i in evidence['items']if i['id']=='rom.weapon.18')['equipment']['allowedCharacters']=['nezha']
            else:next(i for i in evidence['items']if i['id']=='rom.armor.10')['equipment']['slot']='feet'
            def read(path):return evidence if Path(path).resolve()==(ci.ROOT/self.proof).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin,verify_target=False)
    def test_actual_joined_growth_retains_uint16_hp_and_uint8_other_deltas(self):
        rows=json.loads(self.result['combat.json'])['characterGrowth'][0]['rows']
        row=next(r for r in rows if r['level']==59)
        self.assertEqual((992795,256,8,28,15,9,5),tuple(row[k] for k in ['threshold','hp','mp','strength','stamina','agility','spirit']))
        for row in rows:
            self.assertTrue(0<=row['hp']<=65535)
            self.assertTrue(all(0<=row[k]<=255 for k in ['mp','strength','stamina','agility','spirit']))
    def test_target_source_labels_match_existing_loader_without_promoting_provisional_names(self):
        # The previous candidate had valid bytes but invented a composite label
        # rejected by ContentLoader. Keep the existing vocabulary and facts.
        for item in self.scene['items']:
            self.assertIn(item['source']['confidence'],['GAMEPLAY_VERIFIED','PROVISIONAL_REFERENCE'],item['id'])
        for shop in self.scene['shops']:self.assertIn(shop['source']['confidence'],['GAMEPLAY_VERIFIED','ORIGINAL_ROM_STATIC'])
        for inn in self.scene['inns']:self.assertIn(inn['confidence'],['GAMEPLAY_VERIFIED','VERIFIED','ORIGINAL_ROM_STATIC'])
        for iid in ['rom.armor.11','rom.armor.38','rom.armor.10','rom.weapon.18']:
            item=next(i for i in self.scene['items']if i['id']==iid)
            self.assertEqual('PROVISIONAL_REFERENCE',item['source']['confidence'])
            self.assertIn('UNKNOWN',item['source']['nameEvidence'].values())

if __name__=='__main__':unittest.main()
