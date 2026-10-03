"""Scoped forest input, original complete encounters and existing export path."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import digest,extract_encounter_groups

class Forest101ExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-forest101-content.json';cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_exact_maps_groups_and_byte_identical_old_media(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(199,len(self.result));self.assertEqual(34,len(json.loads(self.result['scene.json'])['maps']))
        self.assertEqual(self.pin['manifestSha256'],digest(self.result['manifest.json']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        combat=json.loads(self.result['combat.json']);zone=next(z for z in combat['zones']if z['mapId']==101)
        self.assertEqual(extract_encounter_groups(self.r,17)['groups'],zone['groups'])
        self.assertEqual((17,13,'HIGH',245),(zone['id'],len(zone['groups']),zone['randomGate'],zone['randomThreshold']))
        self.assertEqual({38,39},{e['id']for e in combat['enemies']}-{e['id']for e in json.loads(self.old['combat.json'])['enemies']})
        ci.validate_item_sources(self.result)
    def test_preserves_trees_and_horizontal_source_rule_real_returns(self):
        m=json.loads(self.result['scene101.json']);self.assertEqual([0,3,7,8,9],m['walkableClasses'])
        self.assertEqual({'3':['LEFT','RIGHT']},m['sourceEdges']);self.assertEqual({},m['targetEdges'])
        self.assertEqual(1,m['collision'][52*48+8]);self.assertNotIn(52*48+8,m['enabledCells'])
        self.assertEqual(0,m['collision'][50*48+8]);self.assertIn(50*48+8,m['enabledCells'])
        exits=[e for e in json.loads(self.result['scene.json'])['exits']if 101 in (e['fromMapId'],e['toMapId'])]
        self.assertEqual([(16,[213,155],101,[8,51],'UP'),(101,[8,51],16,[213,155],'DOWN')],[(e['fromMapId'],e['trigger'],e['toMapId'],e['spawn'],e['arrivalDirection'])for e in exits])
        self.assertFalse(any(e['toMapId']==89 for e in exits))
    def test_incomplete_groups_nerfed_stats_guessed_entry_or_open_trees_rejected(self):
        for kind in ('groups','hp','entry','trees','graphic','gate'):
            p=copy.deepcopy(self.p)
            if kind=='groups':p['combatOverlay']['zones'][0]['groups'].pop()
            elif kind=='hp':p['combatOverlay']['enemies'][0]['hp']=1
            elif kind=='entry':p['exits'][0]['spawn'][0]+=1
            elif kind=='trees':p['maps'][0]['walkableClasses'].append(1)
            elif kind=='graphic':p['graphics']['battle-enemy-38.png']['rgbaSha256']='0'*64
            else:p['combatOverlay']['zones'][0]['randomThreshold']=0
            with self.assertRaises(ValueError,msg=kind):self.changed(p)
    def test_rts_minus_one_is_not_a_valid_function_entry(self):
        path=ci.ROOT/'game-data/provenance/world-forest101-terrain.json';proof=json.loads(path.read_text(encoding='utf-8'))
        def load(p):return proof if Path(p).resolve()==path.resolve()else json.loads(Path(p).read_text(encoding='utf-8'))
        for field,value in [('sourceEntry',0xcdbf),('targetEntry',0xd196),('cpuExpectedSha256','0'*64),('mode',3)]:
            old=proof[field];proof[field]=value
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
            proof[field]=old

if __name__=='__main__':unittest.main()
