"""Target content and critical original party/story conditions, not Android play acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter
from forensics.fengshen246 import extract_map

class EastExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-east-party-content.json').read_text(encoding='utf-8'))
        cls.proof=cls.pin['iteration']['provenance']
        cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
        cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_repeatable_export_retains_all_old_media_and_exact_target_manifest(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        golden=json.loads((ci.ROOT/'ci/golden-world-cave85-content.json').read_text(encoding='utf-8'))
        old=exporter.export_from_base(self.base,golden['iteration']['provenance'],golden)
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
        for name,sha in json.loads(self.result['manifest.json'])['files'].items():self.assertEqual(sha,ci.sha(self.result[name]))
    def test_join_is_independent_actor_state_and_current_equipment_not_bag_grant(self):
        actor=next(x for x in self.scene['additionalCharacters']if x['initialState']['id']=='xiaolongnv')
        self.assertEqual((1,12,2000,92,44),(actor['originalActorIndex'],actor['initialState']['level'],actor['initialState']['experience'],actor['initialState']['hp'],actor['initialState']['mp']))
        self.assertEqual({'rightHand':19,'leftHand':-1,'body':11,'feet':38},actor['initialState']['equipment'])
        growth=next(x for x in self.combat['characterGrowth']if x['owner']=='xiaolongnv')
        self.assertEqual(79,len(growth['rows']));self.assertEqual(1972,next(x['threshold']for x in growth['rows']if x['level']==12))
        self.assertEqual(2525,next(x['threshold']for x in growth['rows']if x['level']==13))
        self.assertNotEqual(self.combat['physicalRules']['multiplierThresholds'],self.combat['physicalRules']['characterMultiplierThresholds']['xiaolongnv'])
    def test_real_hell_entry_encounters_are_rectangular_and_unimplemented_regions_remain_visible(self):
        zone=next(x for x in self.combat['zones']if x['mapId']==23)
        self.assertEqual((8,'LOW',16),(zone['id'],zone['randomGate'],zone['randomThreshold']))
        self.assertEqual([[1,72,56,95]],zone['rectangles']);self.assertEqual(13,len(zone['groups']))
        hell=json.loads(self.result['scene23.json']);original=extract_map(exporter.iteration_reader(),23)
        self.assertEqual((original['width'],original['height']), (hell['width'],hell['height']))
        self.assertEqual(original['width']*original['height'],len(hell['grid']))
        self.assertEqual([[1,38,63,71],[1,36,12,38],[16,0,63,37],[1,0,16,32]],hell['unavailableRegions'])
        self.assertEqual(3,hell['terrain']['tileset'])
    def test_story_flags_direction_and_existing_drop_definition_have_distinct_roles(self):
        boss=next(x for x in self.combat['bosses']if x['id']=='rom.boss.141')
        self.assertEqual('rom.map.95.flag.1',boss['flagId'])
        self.assertEqual(['rom.map.95.flag.128'],boss['continuation']['completionFlags'])
        self.assertEqual({'mapId':23,'x':54,'y':92,'encounterSteps':0},boss['continuation']['destination'])
        self.assertTrue(all(x['preserveArrivalDirection']for x in self.scene['exits']if {x['fromMapId'],x['toMapId']}=={25,95}))
        old=next(x for x in json.loads(self.base['scene.json'])['items']if x['id']=='rom.medicine.7')
        self.assertEqual(old,next(x for x in self.scene['items']if x['id']==old['id']))
    def test_wrong_join_growth_multiplier_owner_story_or_reuse_is_rejected_without_hash_gate(self):
        for kind in ('initial-xp','owner','growth','multiplier','equipment','dialogue-order','destination','reward-flag','reuse'):
            evidence=copy.deepcopy(self.evidence);actor=evidence['additionalCharacters'][0];overlay=evidence['combatOverlay']
            boss=next(x for x in overlay['bosses']if x['id']=='rom.boss.141')
            if kind=='initial-xp':actor['initialState']['experience']+=1
            elif kind=='owner':actor['originalActorIndex']=0
            elif kind=='growth':overlay['characterGrowth'][0]['rows'][11]['threshold']+=1
            elif kind=='multiplier':overlay['characterMultiplierThresholds']['xiaolongnv'][0]^=1
            elif kind=='equipment':next(x for x in evidence['items']if x['id']=='rom.weapon.19')['equipment']['allowedCharacters']=['nezha']
            elif kind=='dialogue-order':boss['continuation']['dialogueIds'].reverse()
            elif kind=='destination':boss['continuation']['destination']['x']+=1
            elif kind=='reward-flag':boss['flagId']='rom.map.95.flag.128'
            else:evidence['existingItemReuse'][0]['baseDefinitionSha256']='0'*64
            def read(path):return evidence if Path(path).resolve()==(ci.ROOT/self.proof).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin,verify_target=False)

if __name__=='__main__':unittest.main()
