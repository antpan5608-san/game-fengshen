"""North palace coordinate story and independently granted reusable key."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter
from forensics.fengshen246 import extract_encounter_groups

class NorthPalaceExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-north-palace-content.json').read_text(encoding='utf-8'))
        cls.path=Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk'))
        cls.base=ci.content(cls.path,cls.pin['iteration']['base']);cls.proof=cls.pin['iteration']['provenance']
        cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
        cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_repeatable_from_original_base_and_west_media_stays_byte_identical(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        oldpin=json.loads((ci.ROOT/'ci/golden-world-west-content.json').read_text(encoding='utf-8'))
        old=exporter.export_from_base(self.base,oldpin['iteration']['provenance'],oldpin)
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        for name,sha in json.loads(self.result['manifest.json'])['files'].items():self.assertEqual(sha,ci.sha(self.result[name]))
    def test_all_four_real_edges_and_automatic_guard_before_separate_chest(self):
        edges={(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in self.scene['exits']}
        self.assertTrue({(25,(29,3),98,(7,29)),(98,(7,29),25,(29,3)),(98,(5,6),139,(11,25)),(139,(11,25),98,(5,6))}<=edges)
        boss=next(b for b in self.combat['bosses']if b['id']=='rom.boss.139')
        self.assertEqual((139,2,4),(boss['entryTrigger']['mapId'],boss['entryTrigger']['x'],boss['entryTrigger']['y']))
        self.assertTrue(boss['commitAfterDialogue']);self.assertEqual('rom.map.139.flag.128',boss['flagId'])
        chest=next(n for n in self.scene['npcs']if n['id']==boss['npcId'])
        self.assertEqual([],chest['firstEffects']);self.assertEqual('rom.special.11',chest['treasure']['itemId'])
        self.assertEqual('rom.map.139.flag.2',chest['treasure']['flagId'])
    def test_original_duplicate_slot_record_has_two_instances_without_removing_group(self):
        original=extract_encounter_groups(exporter.iteration_reader(),5)
        self.assertEqual(14,len(original['groups']));self.assertEqual('020d040f020d00',original['groups'][11]['rawHex'])
        self.assertEqual(3,len(original['groups'][11]['entities']))
        for zone in [z for z in self.combat['zones']if z['mapId']in (98,139)]:
            self.assertEqual(14,len(zone['groups']));group=zone['groups'][11]
            self.assertEqual([1,3],[e['slot']for e in group['entities']]);self.assertEqual(3,len(group['sourceEntities']))
            enemies={e['id']:e for e in self.combat['enemies']}
            self.assertEqual(42,sum(enemies[e['enemyId']]['experienceReward']for e in group['entities']))
            self.assertEqual(19,sum(enemies[e['enemyId']]['moneyReward']for e in group['entities']))
    def test_reusable_key_and_original_whirlpool_block_cell(self):
        key=next(i for i in self.scene['items']if i['id']=='rom.special.11')
        self.assertEqual((1,11,'special'),(key['maxCount'],key['originalId'],key['category']))
        self.assertTrue(key['worldUse']['reusable']);self.assertNotIn('buyPrice',key);self.assertNotIn('sellPrice',key)
        armor=next(i for i in self.scene['items']if i['id']=='rom.armor.2')
        self.assertEqual(('魚皮衣',200,12),(armor['name'],armor['buyPrice'],armor['equipment']['defenseBonus']))
        obj=next(o for o in self.scene['mapObjects']if o['id']=='rom.object.25.0')
        self.assertEqual([47,40],obj['cell']);self.assertEqual('rom.map.25.flag.1',obj['itemTarget']['removedFlagId'])
        sea=json.loads(self.result['scene25.json']);self.assertIn(40*64+47,sea['dynamicObjectCells'])
    def test_actual_enemy_stats_ice_and_drops_without_inventing_drop_effect(self):
        enemies={e['id']:e for e in self.combat['enemies']};boss=enemies[139]
        self.assertEqual((200,31,23,110,200,13),tuple(boss[k]for k in ['hp','attack','defense','experienceReward','moneyReward','iceBaseDamage']))
        self.assertEqual(13,enemies[12]['iceBaseDamage'])
        item=next(i for i in self.scene['items']if i['id']=='rom.medicine.2')
        self.assertEqual('PROVISIONAL_REFERENCE',item['source']['confidence']);self.assertNotIn('herbUse',item)
    def test_wrong_story_key_raw_group_and_object_definitions_are_rejected(self):
        for kind in ('trigger','flag','phase','count','price','key-target','chest','object','raw','instance'):
            evidence=copy.deepcopy(self.evidence);boss=next(b for b in evidence['combatOverlay']['bosses']if b['id']=='rom.boss.139')
            key=next(i for i in evidence['items']if i['id']=='rom.special.11')
            group=next(z for z in evidence['combatOverlay']['zones']if z['mapId']==98)['groups'][11]
            if kind=='trigger':boss['entryTrigger']['y']=3
            elif kind=='flag':boss['flagId']='rom.map.139.flag.2'
            elif kind=='phase':boss['commitAfterDialogue']=False
            elif kind=='count':key['maxCount']=10
            elif kind=='price':key['sellPrice']=1
            elif kind=='key-target':key['worldUse']['targetSpriteId']=144
            elif kind=='chest':next(n for n in evidence['npcs']if n['id']=='rom.npc.139.1')['treasure']['amount']=2
            elif kind=='object':evidence['mapObjects'][0]['cell']=[48,40]
            elif kind=='raw':group['sourceEntities'].pop()
            else:group['entities'].append(group['entities'][0])
            def read(path):return evidence if Path(path).resolve()==(ci.ROOT/self.proof).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin)

if __name__=='__main__':unittest.main()
