"""Independent original CPU damage outputs; not normal Android acceptance."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex
from forensics.fengshen246 import extract_enemy_ice_base,Reader

class IceIdentityEvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader();cls.proof=json.loads((ex.ROOT/'game-data/provenance/world-enemy-ice-identities.json').read_text(encoding='utf-8'))
        cls.rows=[list(map(int,l.split('\t')))for l in (ex.ROOT/cls.proof['cpuExpectedPath']).read_text(encoding='utf-8').splitlines()if l and not l.startswith('#')]
    def enemy(self,eid):return {'id':eid,'behaviorByte':3,'iceDamageEvidence':'game-data/provenance/world-enemy-ice-identities.json',**extract_enemy_ice_base(self.r,eid)}
    def test_all_seventeen_identities_match_cpu_for_both_actors_and_armor_extremes(self):
        self.assertEqual(1700,len(self.rows));ids=set()
        for eid,actor,mask,hp,armor,base,damage,after in self.rows:
            ids.add(eid);e=self.enemy(eid);self.assertEqual(base,e['iceBaseDamage'])
            self.assertEqual((base*(2 if mask&4 else 1))&65535,damage);self.assertEqual(max(0,hp-damage),after)
            ex.validate_world_ice_identities(self.r,e)
        self.assertEqual(set(self.proof['verifiedEnemyIds']),ids)
        self.assertEqual(40,self.enemy(150)['iceBaseDamage'])
    def test_wrong_fingerprint_behavior_identity_base_or_source_rejected(self):
        raw=bytearray(self.r.data);raw[32]^=1
        with self.assertRaisesRegex(ValueError,'fingerprint'):extract_enemy_ice_base(Reader(bytes(raw),verify=False),150)
        for eid in [0,143,144,147,177]:
            with self.assertRaises(ValueError):extract_enemy_ice_base(self.r,eid)
        for key,val in [('iceBaseDamage',999),('iceDamageEvidence','unknown'),('behaviorByte',2),('specialBaseDamage',1),('id',143)]:
            e=self.enemy(150);e[key]=val
            with self.assertRaises(ValueError):ex.validate_world_ice_identities(self.r,e)
    def test_formula_modifier_source_and_identity_scope_not_silently_extended(self):
        for case in ['identity','formula','armor','status','death','source']:
            p=copy.deepcopy(self.proof)
            if case=='identity':p['verifiedEnemyIds'].append(144)
            elif case=='formula':p['rules']['ordinaryFormula']='fixed50'
            elif case=='armor':p['rules']['baseDamageIgnoresArmorAndStamina']=False
            elif case=='status':p['rules']['status04DoublesBeforeUint16Truncation']=False
            elif case=='death':p['rules']['directDamageBeforeDeathFlag']=False
            else:p['sources']=[s for s in p['sources']if s['cpuAddress']!=0xa906]
            with patch.object(ex,'load',return_value=p),self.assertRaises(ValueError,msg=case):ex.validate_world_ice_identities(self.r,self.enemy(150))
if __name__=='__main__':unittest.main()
