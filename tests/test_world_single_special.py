"""Derived CPU outputs validate scope and original formulas, not normal App play."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex
from forensics.fengshen246 import extract_enemy,extract_enemy_single_special_base,Reader
class SingleSpecialEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader();cls.proof=json.loads((ex.ROOT/'game-data/provenance/world-enemy-single-special.json').read_text(encoding='utf-8'))
        cls.rows=[l.split('\t')for l in (ex.ROOT/cls.proof['cpuExpectedPath']).read_text(encoding='utf-8').splitlines()if l and not l.startswith('#')]
    def enemy(self,eid):return {'id':eid,'behaviorByte':extract_enemy(self.r,eid)['remainingBytes'][1],'specialDamageEvidence':'game-data/provenance/world-enemy-single-special.json',**extract_enemy_single_special_base(self.r,eid)}
    def test_all_eleven_original_identities_match_captured_damage_for_both_targets(self):
        self.assertEqual(3148,len(self.rows));rows=[r for r in self.rows if r[0]=='H'];self.assertEqual(1100,len(rows));ids=set()
        for r in rows:
            behavior,eid,actor,mask,hp,armor,base,damage,after,status=map(int,r[1:]);ids.add(eid);e=self.enemy(eid)
            self.assertEqual(behavior,e['behaviorByte']);self.assertEqual(base,e['specialBaseDamage'])
            self.assertEqual(max(0,hp-damage),after);ex.validate_world_single_special(self.r,e)
        self.assertEqual(set(sum(self.proof['verifiedEnemyIds'].values(),[])),ids)
    def test_ai_expected_rows_keep_single_target_and_existing_living_retarget_rules(self):
        rows=[r for r in self.rows if r[0]=='D'];self.assertEqual(2048,len(rows))
        for r in rows:
            behavior,eid,hit,byte,alive,special,succeeds,target=map(int,r[1:])
            self.assertEqual(int((byte&127)<41),special);self.assertEqual(int(bool(special)or byte<hit),succeeds)
            start=((byte>>2)&3)%2;expected=next((i for i in list(range(start,2))+list(range(2))if alive&(1<<i)),-1)
            self.assertEqual(expected,target)
    def test_wrong_fingerprint_behavior_damage_or_type_alias_refused(self):
        for eid in [0,24,142,143,144]:
            with self.assertRaises(ValueError):extract_enemy_single_special_base(self.r,eid)
        raw=bytearray(self.r.data);raw[32]^=1
        with self.assertRaisesRegex(ValueError,'fingerprint'):extract_enemy_single_special_base(Reader(bytes(raw),verify=False),147)
        for key,value in [('specialBaseDamage',999),('specialDamageEvidence','unknown'),('behaviorByte',1),('id',143),('iceBaseDamage',10)]:
            e=self.enemy(147);e[key]=value
            with self.assertRaises(ValueError):ex.validate_world_single_special(self.r,e)
    def test_missing_target_sources_or_all_target_alias_rejected(self):
        for case in ['target','source','all','threshold','identity','death']:
            p=copy.deepcopy(self.proof)
            if case=='target':p['sources']=[s for s in p['sources']if s['cpuAddress']!=0x8de9]
            elif case=='source':p['identityBases']['147']['specialBaseDamage']+=1
            elif case=='all':p['rules']['allTarget']=True
            elif case=='threshold':p['rules']['secondaryThresholds']['2']=25
            elif case=='identity':p['verifiedEnemyIds']['2'].append(143)
            else:p['rules']['rawDamageDispatchCapturesDeathFlag']=True
            with patch.object(ex,'load',return_value=p),self.assertRaises(ValueError,msg=case):ex.validate_world_single_special(self.r,self.enemy(147))
if __name__=='__main__':unittest.main()
