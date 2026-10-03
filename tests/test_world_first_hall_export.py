"""Actual original first hall scope; CPU/controlled reward evidence is not normal App acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_map,extract_encounter_groups

class FirstHallExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-first-hall-content.json').read_text(encoding='utf-8'));cls.path=cls.pin['iteration']['provenance']
        cls.proof=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json']);cls.reader=ex.iteration_reader()
    def test_map_collision_and_independent_connections_without_guessed_exit_flags(self):
        data=json.loads(self.result['scene70.json']);original=extract_map(self.reader,70)
        self.assertEqual((32,15), (data['width'],data['height']));self.assertEqual([t for row in original['grid']for t in row],data['grid'])
        self.assertIn(23+2*32,data['dynamicObjectCells']);self.assertEqual(3,data['terrain']['tileset'])
        expected={(23,(28,80),70,(1,13)),(23,(43,75),70,(23,2)),(70,(1,13),23,(28,80)),(70,(23,2),23,(43,75))}
        rows=[e for e in self.scene['exits']if 70 in[e['fromMapId'],e['toMapId']]]
        self.assertEqual(expected,{(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in rows})
        self.assertTrue(all(e['preserveArrivalDirection']and 'requiredFlag'not in e for e in rows))
    def test_full_default_zone_and_actual_enemy142_not_exterior189(self):
        zone=next(z for z in self.combat['zones']if z['mapId']==70)
        self.assertEqual((9,'HIGH',245,[]),(zone['id'],zone['randomGate'],zone['randomThreshold'],zone['rectangles']))
        self.assertEqual(extract_encounter_groups(self.reader,9)['groups'],zone['groups']);self.assertEqual(10,len(zone['groups']))
        boss=next(b for b in self.combat['bosses']if b['id']=='rom.boss.142')
        self.assertEqual((159,142,20,2),(boss['sourceType'],boss['enemyId'],boss['eventId'],boss['eventArgument']))
        enemy=next(e for e in self.combat['enemies']if e['id']==142)
        self.assertEqual((520,55,42,240,350,0,217),(enemy['hp'],enemy['attack'],enemy['defense'],enemy['experienceReward'],enemy['moneyReward'],enemy['behaviorByte'],enemy['hitByte']))
        self.assertEqual(['rom.map.70.flag.4'],boss['victoryFlags']);self.assertEqual('rom.map.70.flag.2',boss['flagId'])
        self.assertNotIn('continuation',boss);self.assertNotIn('reward',boss)
    def test_barrier_and_talk_point_bind_to_original_records(self):
        b=self.scene['sceneBarriers'][0];self.assertEqual((70,[23,2],'rom.map.70.flag.4'),(b['mapId'],b['cell'],b['removedFlagId']))
        npc=next(n for n in self.scene['npcs']if n['id']=='rom.npc.70.1')
        self.assertEqual(([28,4],[28,6],'UP',[]),(npc['cell'],npc['interactionCell'],npc['interactionDirection'],npc['firstEffects']))
        self.assertFalse(any(d['source']['originalVerified']for d in self.scene['dialogues']if d['id'].startswith('rom.dialogue.80.')))
        medicine=next(i for i in self.scene['items']if i['id']=='rom.medicine.11');self.assertNotIn('herbUse',medicine);self.assertNotIn('useEffect',medicine)
    def test_existing_media_bytes_reused_and_manifest_repeatable(self):
        oldpin=json.loads((ci.ROOT/'ci/golden-world-village2-content.json').read_text(encoding='utf-8'))
        old=ex.export_from_base(self.base,oldpin['iteration']['provenance'],oldpin)
        for n,raw in old.items():
            if not n.endswith('.json'):self.assertEqual(raw,self.result[n],n)
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
    def test_wrong_gate_flag_event_source_talk_point_or_incomplete_zone_refused(self):
        for case in ['barrier','event','source','talk','zone']:
            proof=copy.deepcopy(self.proof);boss=next(b for b in proof['combatOverlay']['bosses']if b['id']=='rom.boss.142')
            if case=='barrier':proof['sceneBarriers'][0]['removedFlagId']='rom.map.70.flag.2'
            elif case=='event':boss['eventArgument']=4
            elif case=='source':boss['sourceType']=189
            elif case=='talk':next(n for n in proof['npcs']if n['id']=='rom.npc.70.1')['interactionCell']=[28,5]
            else:next(z for z in proof['combatOverlay']['zones']if z['mapId']==70)['groups'].pop()
            def read(p):return proof if Path(p).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(p).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=read),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
