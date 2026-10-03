"""Full Hell zone preservation and newly supported state08 safety, not App acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter
from forensics.fengshen246 import extract_encounter_groups,extract_enemy

class HellEncounterExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-hell-encounters-content.json').read_text(encoding='utf-8'))
        cls.proof=cls.pin['iteration']['provenance'];cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
        cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_all_three_rectangles_and_original_groups_retain_threshold_and_instance_identity(self):
        r=exporter.iteration_reader()
        expected={8:[[1,72,56,95]],10:[[1,38,63,71],[1,36,12,38]],13:[[16,0,63,37],[1,0,16,32]]}
        zones=[z for z in self.combat['zones']if z['mapId']==23]
        self.assertEqual(set(expected),{z['id']for z in zones})
        for z in zones:
            self.assertEqual(expected[z['id']],z['rectangles']);self.assertEqual(('LOW',16),(z['randomGate'],z['randomThreshold']))
            original=extract_encounter_groups(r,z['id'])['groups']
            self.assertEqual([g['entities']for g in original],[g['entities']for g in z['groups']])
        self.assertFalse(json.loads(self.result['scene23.json']).get('unavailableRegions'))
        self.assertTrue(any('NPCs and onward exits' in x for x in json.loads(self.result['scene23.json'])['limitations']))
    def test_new_stats_behavior_loot_ids_and_provisional_names_do_not_infer_equipment_or_effect(self):
        r=exporter.iteration_reader();items={i['id']:i for i in self.scene['items']}
        for eid in [22,23,28,29,30]:
            e=next(e for e in self.combat['enemies']if e['id']==eid);original=extract_enemy(r,eid)
            self.assertEqual([original[k]for k in ['hp','attack','defense','experienceReward','moneyReward']], [e[k]for k in ['hp','attack','defense','experienceReward','moneyReward']])
            self.assertEqual(original['remainingBytes'][1],e['behaviorByte']);self.assertIn(e['loot']['itemId'],items)
            self.assertEqual('PROVISIONAL_REFERENCE_STAT_TUPLE_MATCH_NOT_ID_ALIAS',e['nameEvidence']['kind'])
        self.assertNotIn('equipment',items['rom.weapon.18']);self.assertNotIn('herbUse',items['rom.medicine.4'])
        self.assertNotIn('antidoteUse',items['rom.medicine.4']);self.assertIn('未核',items['rom.medicine.4']['name'])
    def test_repeatable_new_content_preserves_all_previous_media_and_inventory_definitions(self):
        golden=json.loads((ci.ROOT/'ci/golden-world-east-party-content.json').read_text(encoding='utf-8'))
        old=exporter.export_from_base(self.base,golden['iteration']['provenance'],golden)
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        newitems={i['id']:i for i in self.scene['items']}
        for olditem in json.loads(old['scene.json'])['items']:self.assertEqual(olditem,newitems[olditem['id']])
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
    def test_wrong_behavior_proof_group_loot_or_price_fails_before_target_hash(self):
        bit_path=ci.ROOT/'game-data/provenance/world-status-bit8.json'
        for kind in ['missing-proof','wrong-legal-status','missing-command-progress','wrong-behavior','missing-group','wrong-loot','wrong-price']:
            evidence=copy.deepcopy(self.evidence);status=json.loads(bit_path.read_text(encoding='utf-8'))
            enemy=next(e for e in evidence['combatOverlay']['enemies']if e['id']==29)
            if kind=='missing-proof':enemy.pop('behaviorEvidence')
            elif kind=='wrong-legal-status':status['rules']['legalReplaceMasks']=[0,2,4,8]
            elif kind=='missing-command-progress':status['sources']=[s for s in status['sources']if s['cpuAddress']!=0xb68e]
            elif kind=='wrong-behavior':enemy['behaviorByte']=0
            elif kind=='missing-group':next(z for z in evidence['combatOverlay']['zones']if z['id']==13)['groups'].pop()
            elif kind=='wrong-loot':enemy['loot']['threshold']+=1
            else:next(i for i in evidence['items']if i['id']=='rom.medicine.4')['buyPrice']+=1
            def read(path):
                if Path(path).resolve()==(ci.ROOT/self.proof).resolve():return evidence
                if Path(path).resolve()==bit_path.resolve():return status
                return json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin,verify_target=False)

if __name__=='__main__':unittest.main()
