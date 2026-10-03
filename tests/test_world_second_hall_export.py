"""Second hall source/generation boundaries. No normal App PASS inferred here."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_map,extract_npcs,extract_encounter_groups,extract_enemy_special_base

class SecondHallExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-second-hall-content.json').read_text(encoding='utf-8'));cls.path=cls.pin['iteration']['provenance']
        cls.proof=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'));cls.reader=ex.iteration_reader()
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_actual_geometry_all_npcs_and_each_directed_exit_stay_distinct(self):
        data=json.loads(self.result['scene60.json']);m=extract_map(self.reader,60)
        self.assertEqual((32,30),(data['width'],data['height']));self.assertEqual([t for row in m['grid']for t in row],data['grid'])
        n=extract_npcs(self.reader,60);self.assertEqual(10,len(n['records']))
        self.assertEqual(9,len([a for a in self.scene['npcs']if a['mapId']==60]))
        expected={(60,(28,22),23,(5,70)),(60,(1,28),23,(6,80)),(23,(5,70),60,(28,22)),(23,(6,80),60,(1,28))}
        actual=[e for e in self.scene['exits']if 60 in[e['fromMapId'],e['toMapId']]]
        self.assertEqual(expected,{(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in actual})
        self.assertTrue(all(e['preserveArrivalDirection']and 'requiredFlag'not in e for e in actual))
        gate=next(b for b in self.scene['sceneBarriers']if b['mapId']==60);self.assertEqual('rom.map.60.flag.4',gate['removedFlagId'])
        self.assertIn(28+22*32,data['dynamicObjectCells']);self.assertEqual([28,22],gate['cell'])
    def test_original_boss_identity_special_source_and_full_encounter_table(self):
        e=next(e for e in self.combat['enemies']if e['id']==143)
        self.assertEqual((600,60,40,250,380,1,193),(e['hp'],e['attack'],e['defense'],e['experienceReward'],e['moneyReward'],e['behaviorByte'],e['hitByte']))
        self.assertEqual(15,e['specialBaseDamage']);self.assertNotIn('iceBaseDamage',e)
        self.assertEqual(extract_enemy_special_base(self.reader,143)['specialSource'],e['specialSource'])
        boss=next(b for b in self.combat['bosses']if b['id']=='rom.boss.143')
        self.assertEqual((160,143,21,2),(boss['sourceType'],boss['enemyId'],boss['eventId'],boss['eventArgument']))
        self.assertEqual(['rom.map.60.flag.4'],boss['victoryFlags']);self.assertEqual('rom.map.60.flag.2',boss['flagId'])
        zone=next(z for z in self.combat['zones']if z['mapId']==60)
        self.assertEqual(extract_encounter_groups(self.reader,9)['groups'],zone['groups']);self.assertEqual(('HIGH',245,[]),(zone['randomGate'],zone['randomThreshold'],zone['rectangles']))
    def test_reference_text_and_controlled_interaction_limits_are_not_promoted(self):
        rows=[d for d in self.scene['dialogues']if d['id'].startswith('rom.dialogue.70.')];self.assertEqual(9,len(rows))
        verified=[d['id']for d in rows if d['source']['originalVerified']];self.assertEqual(['rom.dialogue.70.0'],verified)
        npcs=[n for n in self.scene['npcs']if n['mapId']==60];self.assertTrue(all(not n['firstEffects']for n in npcs))
        script=json.loads((ci.ROOT/'game-data/provenance/world-second-hall-script.json').read_text(encoding='utf-8'))
        self.assertIn('NOT_PASSED',script['controlledVictory']['controllerExit']);self.assertIn('NOT_RUN',script['normalEvidence']['kingRoute'])
        self.assertEqual('CONTROLLED_POSITION_DISPATCH_NOT_NORMAL_ROUTE',script['rules']['interactionEvidenceKind'])
        self.assertTrue(next(n for n in npcs if n['id']=='rom.npc.60.3')['source']['remainingUnknown'])
    def test_clean_generation_and_original_media_reuse_are_byte_exact(self):
        golden=json.loads((ci.ROOT/'ci/golden-world-first-hall-content.json').read_text(encoding='utf-8'))
        old=ex.export_from_base(self.base,golden['iteration']['provenance'],golden)
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin));self.assertEqual(127,len(self.result))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
    def test_wrong_price_reward_gate_source_damage_or_short_zone_rejected(self):
        for case in ['reward','flag','source','damage','evidence','zone','barrier']:
            proof=copy.deepcopy(self.proof);boss=next(b for b in proof['combatOverlay']['bosses']if b['id']=='rom.boss.143');enemy=next(e for e in proof['combatOverlay']['enemies']if e['id']==143)
            if case=='reward':enemy['moneyReward']=381
            elif case=='flag':boss['victoryFlags']=['rom.map.60.flag.2']
            elif case=='source':boss['sourceType']=189
            elif case=='damage':enemy['specialBaseDamage']=50
            elif case=='evidence':enemy['specialDamageEvidence']='unknown'
            elif case=='zone':next(z for z in proof['combatOverlay']['zones']if z['mapId']==60)['groups'].pop()
            else:next(b for b in proof['sceneBarriers']if b['mapId']==60)['removedFlagId']='rom.map.60.flag.2'
            def read(p):return proof if Path(p).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(p).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=read),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
