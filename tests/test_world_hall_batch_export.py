"""Scoped batch content gates; controlled state/CPU is not normal Android acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_map,extract_encounter_groups

class HallBatchExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-hall-batch-content.json').read_text(encoding='utf-8'));cls.path=cls.pin['iteration']['provenance']
        cls.proof=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'));cls.reader=ex.iteration_reader()
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_original_geometry_complete_groups_and_independent_flags(self):
        for mid in [61,62,63,64,65,66]:
            data=json.loads(self.result[f'scene{mid}.json']);original=extract_map(self.reader,mid)
            self.assertEqual([t for row in original['grid']for t in row],data['grid']);self.assertEqual(3,data['terrain']['tileset'])
            zone=next(z for z in self.combat['zones']if z['mapId']==mid)
            self.assertEqual(extract_encounter_groups(self.reader,zone['id'])['groups'],zone['groups'])
            rule=ex.validate_world_hall_batch_script(self.reader,mid);boss=next(b for b in self.combat['bosses']if b.get('mapId')==mid)
            self.assertEqual(rule['enemyId'],boss['enemyId']);self.assertEqual(rule['victoryFlags'],boss['victoryFlags'])
            npc=next(n for n in self.scene['npcs']if n['id']==rule['npcId']);self.assertEqual(rule['normalTalkCell'],npc['interactionCell'])
            rows=[e for e in self.scene['exits']if mid in [e['fromMapId'],e['toMapId']]]
            self.assertGreaterEqual(len(rows),4);self.assertTrue(all('requiredFlag'not in e and e['preserveArrivalDirection']for e in rows))
        self.assertEqual([850,1150,1300,1540,1860,2100],[next(e['hp']for e in self.combat['enemies']if e['id']==i)for i in [144,145,146,147,148,149]])
    def test_chests_are_real_category_grants_not_medicine_effects(self):
        chests=[n for n in self.scene['npcs']if n['mapId']in [61,62,63,64,65,66] and n.get('treasure')]
        self.assertEqual(6,len(chests))
        for n in chests:
            ex.validate_world_chest_grant(self.reader,n);self.assertEqual(1,n['treasure']['amount']);self.assertTrue(n['openedSprite'])
            item=next(i for i in self.scene['items']if i['id']==n['treasure']['itemId']);self.assertEqual(item['category'],['medicine','special','weapon','armor'][n['treasure']['categoryGrant']])
        med=next(i for i in self.scene['items']if i['id']=='rom.medicine.8')
        self.assertNotIn('herbUse',med);self.assertNotIn('worldUse',med);self.assertIn('未核',med['name'])
        self.assertEqual('NOT_RUN',self.proof['evidence']['hellHallBatch']['normalAndroid'])
        self.assertIn(64,[m['id']for m in self.scene['maps']]);self.assertNotIn(68,[m['id']for m in self.scene['maps']])
        hidden=next(o for o in self.scene['mapObjects']if o['id']=='rom.npc.64.9')
        self.assertEqual('NOT_IMPLEMENTED',hidden['interaction'])
        proof=json.loads((ci.ROOT/'game-data/provenance/world-hell-hall-batch-resources.json').read_text(encoding='utf-8'))
        self.assertFalse(proof['initialHiddenObjects']['198']['recipe']['completeGraphic'])
        self.assertEqual(0,proof['initialHiddenObjects']['198']['recipe']['opaquePixelCount'])
        self.assertIn('198',proof['unresolvedNpcSprites'])
        rows=self.proof['evidence']['unresolvedExitRows']
        self.assertEqual(2,len(rows));self.assertTrue(all(r['status']=='UNRESOLVED_OUTSIDE_CURRENT_GEOMETRY_NOT_ENABLED'for r in rows))
        self.assertFalse(any(e['fromMapId']==23 and e['trigger']==[12,75]for e in self.scene['exits']))
    def test_repeatable_generation_and_all_old_media_byte_exact(self):
        oldpin=json.loads((ci.ROOT/'ci/golden-world-second-hall-content.json').read_text(encoding='utf-8'))
        old=ex.export_from_base(self.base,oldpin['iteration']['provenance'],oldpin)
        for name,data in old.items():
            if not name.endswith('.json'):self.assertEqual(data,self.result[name],name)
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(162,len(self.result));self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
        self.assertEqual(25,len(self.scene['maps']))
    def test_wrong_chest_reward_king_gate_target_pose_or_zone_rejected(self):
        for case in ['grant','item','gate','boss','talk','npc','zone']:
            p=copy.deepcopy(self.proof);king=next(n for n in p['npcs']if n['id']=='rom.npc.61.1');chest=next(n for n in p['npcs']if n['id']=='rom.npc.61.8')
            if case=='grant':chest['treasure']['amount']=2
            elif case=='item':chest['treasure']['itemId']='rom.medicine.0'
            elif case=='gate':next(b for b in p['sceneBarriers']if b['mapId']==62)['removedFlagId']='rom.map.62.flag.4'
            elif case=='boss':next(b for b in p['combatOverlay']['bosses']if b['mapId']==61)['enemyId']=143
            elif case=='talk':king['interactionCell']=[24,6]
            elif case=='npc':p['graphics']['npc-192-hell-batch.png']['opaquePixelMatch']=False
            else:next(z for z in p['combatOverlay']['zones']if z['mapId']==61)['groups'].pop()
            def read(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=read),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
