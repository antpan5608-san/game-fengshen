"""Original three side rooms and encounter regions; not normal App acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_encounter_groups

class SeventhSideExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-seventh-side-content.json').read_text(encoding='utf-8'))
        cls.path=cls.pin['iteration']['provenance'];cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json']);cls.r=ex.iteration_reader()
    def test_original_maps_independent_returns_full_encounters_and_actual_dialogue_identity(self):
        for mid,zone_id in [(69,15),(158,10),(159,10)]:
            ex.validate_world_hall_batch_terrain(self.r,mid,'game-data/provenance/world-seventh-side-terrain.json')
            zone=next(z for z in self.combat['zones']if z['mapId']==mid)
            self.assertEqual(zone_id,zone['id']);self.assertEqual(extract_encounter_groups(self.r,zone_id)['groups'],zone['groups'])
            self.assertEqual(('HIGH',245),(zone['randomGate'],zone['randomThreshold']))
            exits=[x for x in self.scene['exits']if mid in [x['fromMapId'],x['toMapId']]]
            self.assertEqual(4,len(exits));self.assertTrue(all(x['preserveArrivalDirection']for x in exits))
            npc=next(n for n in self.scene['npcs']if n['id']==f'rom.npc.{mid}.1')
            self.assertEqual('npc-191-seventh-side.png',npc['sprite'])
        self.assertEqual('rom.dialogue.168.1',next(n for n in self.scene['npcs']if n['id']=='rom.npc.158.0')['firstDialogue'])
        self.assertEqual('PROVISIONAL_REFERENCE',next(d for d in self.scene['dialogues']if d['id']=='rom.dialogue.169.2')['source']['confidence'])
        self.assertFalse(any(d['id']=='rom.dialogue.77.0'for d in self.scene['dialogues']))
    def test_repeatable_generation_keeps_all_previous_media(self):
        pin=json.loads((ci.ROOT/'ci/golden-world-seventh-hall-content.json').read_text(encoding='utf-8'))
        old=ex.export_from_base(self.base,pin['iteration']['provenance'],pin)
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(174,len(self.result));self.assertEqual(29,len(self.scene['maps']))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
    def test_unverified_npc_pose_missing_group_or_swapped_return_is_rejected(self):
        for case in ['sprite','zone','return']:
            p=copy.deepcopy(self.p)
            if case=='sprite':p['graphics']['npc-191-seventh-side.png']['rgbaSha256']='0'*64
            elif case=='zone':next(z for z in p['combatOverlay']['zones']if z['mapId']==69)['groups'].pop()
            else:next(e for e in p['exits']if e['fromMapId']==69)['spawn']=[7,13]
            def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)

if __name__=='__main__':unittest.main()
