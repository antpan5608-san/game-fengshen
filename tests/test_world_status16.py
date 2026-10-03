"""Original behavior6 source and CPU-output boundaries; normal App acceptance is separate."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import export_development as ex
from forensics.fengshen246 import extract_enemy
class Status16EvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r=ex.iteration_reader();cls.p=json.loads((ex.ROOT/'game-data/provenance/world-enemy-status16.json').read_text(encoding='utf-8'))
        cls.rows=[l.split('\t')for l in (ex.ROOT/cls.p['cpuExpectedPath']).read_text(encoding='utf-8').splitlines()if l and not l.startswith('#')]
    def enemy(self,eid):return {'id':eid,'behaviorByte':extract_enemy(self.r,eid)['remainingBytes'][1],'behaviorEvidence':'game-data/provenance/world-enemy-status16.json'}
    def test_all_original_identities_and_ai_rows_keep_missed_special_nonphysical(self):
        ids=[i for i in range(177)if extract_enemy(self.r,i)['remainingBytes'][1]==6]
        self.assertEqual(ids,self.p['verifiedEnemyIds']);self.assertEqual(10,len(ids))
        for eid in ids:ex.validate_world_status16(self.r,self.enemy(eid))
        rows=[r for r in self.rows if r[0]=='D'];self.assertEqual(1024,len(rows));self.assertEqual(10,self.r.read(9,0x8dc4+6)[0])
        for r in rows:
            byte,alive,choice,statushit,hit,target=map(int,r[1:]);self.assertEqual(int((byte&127)<41),choice)
            self.assertEqual(int(bool(choice)and ((byte>>1)&63)<10),statushit)
            self.assertEqual(statushit if choice else int(byte<181),hit)
            if choice and not statushit:self.assertFalse(hit)
    def test_original_status_priority_and_defeat_are_not_modern_rpg_guesses(self):
        self.assertEqual(2575,len(self.rows));rows=[r for r in self.rows if r[0]=='S'];self.assertEqual(1536,len(rows))
        for r in rows:
            actor,mask,hp,after,damage=map(int,r[1:]);self.assertEqual(16 if mask in [0,2,4,8,16]else mask,after);self.assertEqual(0,damage)
        for r in [r for r in self.rows if r[0]=='F']:
            a,b,defeat=map(int,r[1:]);self.assertEqual(int(bool((a==32 and b==32)or(a&16 and b&16))),defeat)
    def test_wrong_type_threshold_priority_cure_alias_or_missing_source_rejected(self):
        for eid in [10,143,145,146]:
            with self.assertRaises(ValueError):ex.validate_world_status16(self.r,self.enemy(eid))
        for case in ['threshold','priority','miss','hp','defeat','source','damage']:
            p=copy.deepcopy(self.p);enemy=self.enemy(39)
            if case=='threshold':p['rules']['secondaryHit']='((random>>1)&63)<25'
            elif case=='priority':p['rules']['legalReplaceMasks'].append(32)
            elif case=='miss':p['rules']['selectedSpecialMissIsNotPhysical']=False
            elif case=='hp':p['rules']['hpChangedByStatus']=True
            elif case=='defeat':p['rules']['defeatCondition']='all living actors disabled'
            elif case=='source':p['sources']=[s for s in p['sources']if s['cpuAddress']!=0xb68e]
            else:enemy['specialBaseDamage']=99
            with patch.object(ex,'load',return_value=p),self.assertRaises(ValueError,msg=case):ex.validate_world_status16(self.r,enemy)
if __name__=='__main__':unittest.main()
