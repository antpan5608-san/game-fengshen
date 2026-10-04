"""Actual island evidence and export guards; controlled probes are not normal App proof."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_npcs,extract_encounter_groups

class IslandExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path='game-data/provenance/world-island-content.json'
        cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.pin=copy.deepcopy(cls.parent);cls.pin.update(contentVersion='opening-segment-001-c42')
        cls.pin['iteration']['provenance']=cls.path
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False)
        cls.pin['manifestSha256']=ci.sha(cls.result['manifest.json']);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_reproducible_scoped_batch_and_unchanged_media(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(235,len(self.result));self.assertEqual(44,len(json.loads(self.result['scene.json'])['maps']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        ci.validate_item_sources(self.result)
    def test_original_connections_encounters_and_optional_pending_actors(self):
        scene=json.loads(self.result['scene.json']);combat=json.loads(self.result['combat.json'])
        expected={(79,7,8,78,1,12),(78,1,12,79,7,8),(78,8,2,77,3,7),(77,3,7,78,8,2),
            (78,13,2,77,9,7),(77,9,7,78,13,2),(78,14,10,77,10,12),(77,10,12,78,14,10),
            (77,1,2,76,14,13),(76,14,13,77,1,2)}
        actual={(e['fromMapId'],*e['trigger'],e['toMapId'],*e['spawn'])for e in scene['exits']if e['fromMapId']in [76,77,78,79]and e['toMapId']in [76,77,78,79]}
        self.assertEqual(expected,actual)
        for mid in [77,78]:
            z=next(z for z in combat['zones']if z['mapId']==mid)
            self.assertEqual((22,'HIGH',245),(z['id'],z['randomGate'],z['randomThreshold']))
            self.assertEqual(extract_encounter_groups(self.r,22)['groups'],z['groups'])
        self.assertFalse(any(z['mapId']==76 for z in combat['zones']))
        self.assertFalse(any(o['mapId']==78 for o in scene['mapObjects']))
        residents=[n for n in scene['npcs']if n['mapId']==78];self.assertEqual(2,len(residents))
        for n in residents:ex.validate_world_island_talk(self.r,n)
        self.assertNotIn((76,153,128,16,2,27),actual) # Inapplicable out-of-bounds record is not a fake exit.
    def test_composite_story_actual_intro_and_finalization(self):
        p=ex.validate_world_island_event7(self.r);boss=p['boss']
        self.assertEqual([0,2,4,6],[m['slot']for m in boss['group']['entities']])
        self.assertEqual([152,153,154,155],[m['enemyId']for m in boss['group']['entities']])
        self.assertEqual({'mapId':76,'x':12,'y':12,'evidence':'game-data/provenance/world-island-event7.json'},boss['entryTrigger'])
        self.assertTrue(boss['finalizeWithoutDialogue']);self.assertFalse(boss['commitAfterDialogue'])
        self.assertEqual(['rom.global.7c6.16'],boss['victoryFlags']);self.assertNotIn('continuation',boss)
        scene=json.loads(self.result['scene.json']);actors=[n for n in scene['npcs']if n['mapId']==76 and n['spriteId']!=144]
        self.assertEqual(4,len(actors))
        for n in actors:self.assertTrue(n['automaticStoryOnly']);self.assertEqual('rom.map.76.flag.128',n['removedFlagId']);self.assertFalse(n['firstEffects'])
    def test_actual_five_item_chests_and_distinct_money_cap(self):
        scene=json.loads(self.result['scene.json']);actors=[n for n in scene['npcs']if n['mapId']==76 and n['spriteId']==144]
        self.assertEqual(6,len(actors));self.assertEqual(5,len([n for n in actors if 'treasure'in n]))
        for n in actors:
            if 'treasure'in n:ex.validate_world_chest_grant(self.r,n)
            else:
                ex.validate_world_island_money(self.r,n)
                self.assertEqual((100,999999),(n['moneyTreasure']['amount'],n['moneyTreasure']['moneyCap']))
        item=next(i for i in scene['items']if i['id']=='rom.medicine.3')
        self.assertEqual((500,250,10),(item['buyPrice'],item['sellPrice'],item['maxCount']))
        self.assertFalse(any(k in item for k in ['herbUse','battleUse','antidoteUse']));self.assertIn('未核',item['name'])
    def test_guessed_composite_or_double_reward_actor_or_money_rejected(self):
        for kind in ['source','group','intro','victory','actor','amount','cap','chest','talk','protection']:
            p=copy.deepcopy(self.p);b=p['combatOverlay']['bosses'][0]
            if kind=='source':b['sourceType']=189
            elif kind=='group':b['group']['entities']=[{'slot':3,'enemyId':152}]
            elif kind=='intro':b['intro']['completedSteps']=0
            elif kind=='victory':b['finalizeWithoutDialogue']=False
            elif kind=='actor':p['npcs'][1]['firstEffects']=[{'type':'money','amount':100}]
            elif kind=='amount':p['npcs'][6]['moneyTreasure']['amount']=1000
            elif kind=='cap':p['npcs'][6]['moneyTreasure']['moneyCap']=9999999
            elif kind=='chest':p['npcs'][4]['treasure']['flagId']='rom.map.76.flag.4'
            elif kind=='talk':p['npcs'][-1]['originalTalk']['actionId']=50
            else:p['combatOverlay']['enemies'][0]['requiredBindingMarker']=0
            with self.assertRaises(ValueError,msg=kind):self.changed(p)
    def test_no_free_damage_or_name_inferred_special_item_gate(self):
        combat=json.loads(self.result['combat.json'])
        for e in [e for e in combat['enemies']if e['id']in [152,153,154,155]]:
            proof=ex.validate_world_island_binding(self.r,e)
            self.assertEqual(1,e['requiredBindingMarker']);self.assertEqual(9,proof['itemOriginalId'])
        scene=json.loads(self.result['scene.json'])
        self.assertFalse(any(i['id']=='rom.special.9'for i in scene['items'])) # Acquisition/use intentionally not faked.
        self.assertFalse(self.p['combatOverlay']['bosses'][0].get('requiredInventory'))
    def test_terrain_evidence_and_literal_trigger_not_camera_coordinates(self):
        for mid in [76,77,78]:
            m=next(m for m in self.p['maps']if m['mapId']==mid)
            ex.validate_world_hall_batch_terrain(self.r,mid,m['terrain']['evidence'])
        self.assertEqual(bytes([12,12,0]),self.r.read(11,0xda90,3))
        self.assertNotEqual(bytes([5,5,0]),self.r.read(11,0xda90,3))

if __name__=='__main__':unittest.main()
