"""Scoped actor1 resource sources, not normal Android party or battle acceptance."""
import copy
import io
import json
import sys
import unittest
from pathlib import Path

from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from export_development import checked_span,iteration_reader,scoped_observed_graphic
from forensics.fengshen246 import digest,extract_growth_candidates,extract_world_service_catalog


class XiaolongnvResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=iteration_reader()
        cls.proof=json.loads((ROOT/'game-data/provenance/world-party-xiaolongnv.json').read_text(encoding='utf-8'))

    def test_growth_uses_actor_one_and_actual_cap_not_nezha_or_ui_cache(self):
        proof=self.proof['growthExtension'];groups=extract_growth_candidates(self.reader)['groups']
        self.assertEqual(1,proof['actorIndex']);self.assertEqual('xiaolongnv',proof['owner'])
        for key in ('growthRange','thresholdRange'):
            self.assertEqual(groups[1][key],proof[key]);checked_span(self.reader,proof[key])
            self.assertNotEqual(groups[0][key]['sha256'],proof[key]['sha256'])
        self.assertEqual(bytes.fromhex('bd0405c94fb036'),checked_span(self.reader,proof['levelCapSource']))
        self.assertEqual(80,proof['maxLevel']);self.assertEqual([2,80],proof['levels'])
        rows=groups[1]['rows'];interval=proof['initialInterval']
        self.assertEqual(rows[11]['cumulativeExpCandidate'],interval['currentThreshold'])
        self.assertEqual(rows[12]['cumulativeExpCandidate'],interval['nextThreshold'])
        self.assertEqual((1972,2525,28,525),(interval['currentThreshold'],interval['nextThreshold'],interval['earnedInLevel'],interval['neededForNext']))
        self.assertTrue(all(a['cumulativeExpCandidate']<b['cumulativeExpCandidate'] for a,b in zip(rows,rows[1:])))
        self.assertEqual('NOT_RUN',proof['normalCharacterLevelUp'])

    def test_initial_character_is_event_join_and_does_not_grant_bag_or_reset_nezha(self):
        actor=self.proof['initialCharacter']
        self.assertEqual(('xiaolongnv',1,2,1),(actor['id'],actor['actorIndex'],actor['originalActiveActorId'],actor['partySlot']))
        self.assertEqual((12,2000,92,92,44,44,22,14,26,43),tuple(actor[k] for k in
            ('level','experience','hp','maxHp','mp','maxMp','strength','stamina','agility','spirit')))
        self.assertEqual(dict(rightHand=19,leftHand=-1,body=11,feet=38),actor['equipment'])
        for span in self.proof['initializationSources']:checked_span(self.reader,span)
        boundary=self.proof['atomicJoinBoundary']
        self.assertEqual((28,33,23,[54,92],129),tuple(boundary[k] for k in
            ('event','afterRequiredDialogueScript','mapId','spawn','map95FlagValue')))
        self.assertNotIn('inventory',actor)

    def test_equipment_owner_slot_values_prices_and_hit_match_original_tables(self):
        r=self.reader;catalog={x['id']:x for x in extract_world_service_catalog(r)['items']}
        for item in self.proof['items']:
            original=catalog[item['id']];rule=item['equipment']
            for key in ('category','originalId','buyPrice','sellPrice','maxCount'):self.assertEqual(original[key],item[key])
            expected=(original['contribution'] if item['category']=='weapon' else 0,
                original['contribution'] if rule['slot']=='body' else 0,
                original['contribution'] if rule['slot']=='feet' else 0)
            self.assertEqual(expected,tuple(rule[k]for k in ('attackBonus','defenseBonus','evasionValue')))
            self.assertEqual(['xiaolongnv'],rule['allowedCharacters']);self.assertFalse(rule['crossHandOccupancy'])
            membership=[]
            for actor in range(4):
                root=r.word(2,0xef60+2*actor)
                for slot in range(4):
                    if (slot<2)!=(item['category']=='weapon'):continue
                    data=r.read(2,r.word(2,root+2*slot),64);ids=list(data[:data.index(255)])
                    if item['originalId'] in ids:membership.append((actor,slot))
            self.assertEqual([(1,0),(1,1)] if item['category']=='weapon' else
                [(1,2 if rule['slot']=='body' else 3)],membership)
            for span in rule['ruleSources']:checked_span(r,span)
        weapon=next(i for i in self.proof['items'] if i['id']=='rom.weapon.19')
        self.assertEqual(51,checked_span(r,weapon['weaponHitThreshold']['source'])[0])
        self.assertEqual('NORMAL_ORIGINAL_EQUIPMENT_MENU',weapon['source']['nameEvidence']['kind'])
        for item in self.proof['items'][1:]:
            self.assertEqual('PROVISIONAL_REFERENCE',item['source']['nameEvidence']['kind'])
            self.assertNotIn('name',item['source']['verifiedFields'])

    def test_recipes_reproduce_full_original_compositions_and_fixed_pixels(self):
        r=self.reader;results={x['asset']:x for x in self.proof['verification']['graphics']}
        for item in self.proof['items']:
            asset=item['preview']['asset'];recipe=self.proof['graphics'][asset]
            typ=0 if item['category']=='weapon' else 1;ident=item['originalId']
            ptr=r.word(2,r.word(2,0xf002+2*typ)+2*ident);width,height,pal=r.read(2,ptr,3)
            self.assertEqual((width*8,height*8),(recipe['width'],recipe['height']))
            composition=checked_span(r,recipe['compositionSource']);self.assertEqual(r.read(2,ptr,3+width*height),composition)
            bank=checked_span(r,recipe['chrBankSource'])[0];base=0x80010+2048*bank
            for index,tile in enumerate(recipe['tiles']):
                self.assertEqual([index%width*8,index//width*8],tile['xy'])
                self.assertEqual(base+16*composition[3+index],tile['offset'])
            first=scoped_observed_graphic(r,recipe);second=scoped_observed_graphic(r,recipe)
            self.assertEqual(first,second);self.assertEqual(results[asset]['pngSha256'],digest(first))
            self.assertEqual(recipe['rgbaSha256'],digest(Image.open(io.BytesIO(first)).convert('RGBA').tobytes()))
            self.assertEqual('PASS' if typ==0 else 'NOT_RUN',recipe['normalItemPreviewComparison'])

    def test_recipe_rejects_changed_hash_or_shifted_composition(self):
        original=self.proof['graphics']['item-weapon-19.png']
        changed=copy.deepcopy(original);changed['tiles'][0]['sha256']='0'*64
        with self.assertRaises(ValueError):scoped_observed_graphic(self.reader,changed)
        shifted=copy.deepcopy(original);shifted['tiles'][0]['xy']=[8,0]
        with self.assertRaises(ValueError):scoped_observed_graphic(self.reader,shifted)
        pixels=copy.deepcopy(original);pixels['rgbaSha256']='0'*64
        with self.assertRaises(ValueError):scoped_observed_graphic(self.reader,pixels)


if __name__=='__main__':unittest.main()
