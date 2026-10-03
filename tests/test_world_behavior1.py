"""Original scoped behavior1 input; controlled CPU expectations are not normal App proof."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex
from forensics.fengshen246 import extract_enemy_special_base,Reader

class Behavior1EvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=ex.iteration_reader()
        cls.proof=json.loads((ex.ROOT/'game-data/provenance/world-enemy-behavior1.json').read_text(encoding='utf-8'))
        cls.rows=[r.split('\t') for r in (ex.ROOT/'android/app/src/test/resources/enemy-behavior1-cpu.tsv').read_text(encoding='utf-8').splitlines() if r and not r.startswith('#')]
    def enemy(self,eid):return {'id':eid,'behaviorByte':1,'specialDamageEvidence':'game-data/provenance/world-enemy-behavior1.json',**extract_enemy_special_base(self.reader,eid)}
    def test_all_actual_identities_use_cpu_damage_and_checked_original_spans(self):
        self.assertEqual(444,len(self.rows))
        rows=[r for r in self.rows if r[0]=='I'];self.assertEqual(28,len(rows))
        ids=set()
        for r in rows:
            enemy,actor,mask,base,damage,before,after=map(int,r[1:]);ids.add(enemy)
            actual=extract_enemy_special_base(self.reader,enemy)
            self.assertEqual(base,actual['specialBaseDamage']);ex.checked_span(self.reader,actual['specialSource'])
            self.assertEqual(before-damage,after);ex.validate_world_behavior1(self.reader,self.enemy(enemy))
        self.assertEqual(set(self.proof['verifiedEnemyIds']),ids)
        self.assertEqual(15,self.enemy(143)['specialBaseDamage'])
    def test_wrong_rom_behavior_damage_or_unverified_identity_refused(self):
        for enemy in [0,12,142,144]:
            with self.assertRaisesRegex(ValueError,'behavior1'):extract_enemy_special_base(self.reader,enemy)
        raw=bytearray(self.reader.data);raw[32]^=1
        with self.assertRaisesRegex(ValueError,'fingerprint'):extract_enemy_special_base(Reader(bytes(raw),verify=False),143)
        for key,value in [('specialBaseDamage',14),('specialDamageEvidence','unknown'),('id',142),('iceBaseDamage',15)]:
            enemy=self.enemy(143);enemy[key]=value
            with self.assertRaises(ValueError):ex.validate_world_behavior1(self.reader,enemy)
    def test_missing_target_or_status_damage_sources_not_accepted(self):
        for bad in ['target','damage','scope','armor']:
            proof=copy.deepcopy(self.proof)
            if bad=='target':proof['sources']=[s for s in proof['sources']if s['cpuAddress']!=0x8de9]
            elif bad=='damage':proof['sources']=[s for s in proof['sources']if s['cpuAddress']!=0xab6d]
            elif bad=='scope':proof['verifiedEnemyIds'].append(144)
            else:proof['rules']['baseDamageIgnoresArmorAndStamina']=False
            with patch.object(ex,'load',return_value=proof),self.assertRaises(ValueError,msg=bad):
                ex.validate_world_behavior1(self.reader,self.enemy(143))
if __name__=='__main__':unittest.main()
