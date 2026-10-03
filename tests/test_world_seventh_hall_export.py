"""Scoped original map67 content; CPU/state fixtures are not normal App traversal."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_encounter_groups

class SeventhHallExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        cls.path=cls.pin['iteration']['provenance'];cls.proof=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json']);cls.r=ex.iteration_reader()
    def test_original_enemy_groups_gate_chests_and_scene_item_use(self):
        zone=next(z for z in self.combat['zones']if z['mapId']==67)
        self.assertEqual(15,zone['id']);self.assertEqual(extract_encounter_groups(self.r,15)['groups'],zone['groups'])
        enemy=next(e for e in self.combat['enemies']if e['id']==150)
        self.assertEqual((2500,3,40),(enemy['hp'],enemy['behaviorByte'],enemy['iceBaseDamage']))
        boss=next(b for b in self.combat['bosses']if b.get('mapId')==67)
        rule=ex.validate_world_hall_batch_script(self.r,67);self.assertEqual(rule['victoryFlags'],boss['victoryFlags'])
        chests=[n for n in self.scene['npcs']if n['mapId']==67 and n.get('treasure')]
        self.assertEqual({'rom.special.12','rom.weapon.20','rom.medicine.1'},{n['treasure']['itemId']for n in chests})
        for n in chests:ex.validate_world_chest_grant(self.r,n)
        item=next(i for i in self.scene['items']if i['id']=='rom.special.12')
        ex.validate_world_field_protection_item(self.r,item)
        self.assertNotIn('equipment',next(i for i in self.scene['items']if i['id']=='rom.weapon.20'))
        self.assertEqual('NOT_RUN',self.proof['evidence']['seventhHall']['normalAndroid'])
    def test_repeatable_generation_preserves_previous_media_and_pin(self):
        p=json.loads((ci.ROOT/'ci/golden-world-hall-batch-content.json').read_text(encoding='utf-8'))
        old=ex.export_from_base(self.base,p['iteration']['provenance'],p)
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(167,len(self.result));self.assertEqual(26,len(self.scene['maps']))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
    def test_unverified_grants_effects_hp_or_protection_are_rejected(self):
        for case in ['grant','damage','use','price','identity']:
            p=copy.deepcopy(self.proof);item=next(i for i in p['items']if i['id']=='rom.special.12')
            if case=='grant':next(n for n in p['npcs']if n['id']=='rom.npc.67.2')['treasure']['itemId']='rom.special.11'
            elif case=='damage':next(e for e in p['combatOverlay']['enemies']if e['id']==150)['iceBaseDamage']=10
            elif case=='use':item['fieldProtectionUse']['mapId']=23
            elif case=='price':item['buyPrice']=1
            else:item['name']='辟火罩'
            def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)

if __name__=='__main__':unittest.main()
