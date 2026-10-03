"""Scoped continent bridges and complete original encounters; not Android play evidence."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_encounter_groups,digest

class ContinentBridgeExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-continent-bridge-content.json';cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_complete_original_groups_deterministic_media_and_map_count(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(195,len(self.result));self.assertEqual(33,len(json.loads(self.result['scene.json'])['maps']))
        self.assertEqual(self.pin['manifestSha256'],digest(self.result['manifest.json']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        combat=json.loads(self.result['combat.json']);zone=next(z for z in combat['zones']if z['mapId']==16 and z['id']==16)
        self.assertEqual(19,len(zone['groups']));self.assertEqual(extract_encounter_groups(self.r,16)['groups'],zone['groups'])
        self.assertEqual(('LOW',16,[[193,152,245,169]]),(zone['randomGate'],zone['randomThreshold'],zone['rectangles']))
        self.assertEqual({35,36,37},{e['id']for e in combat['enemies']}-{e['id']for e in json.loads(self.old['combat.json'])['enemies']})
        ci.validate_item_sources(self.result)
    def test_bridge_classes_preserve_grid_existing_cells_regions_and_physical_walls(self):
        old=json.loads(self.old['scene16.json']);new=json.loads(self.result['scene16.json'])
        for k in ('grid','collision','transitionCells','unavailableRegions'):self.assertEqual(old.get(k),new.get(k),k)
        self.assertEqual([0,2,15,16],new['walkableClasses'])
        self.assertEqual({'15':['LEFT','RIGHT'],'16':['UP','DOWN']},new['sourceEdges']);self.assertEqual({},new['targetEdges'])
        expected=set(old['enabledCells'])|{i for i,c in enumerate(old['collision'])if c in (15,16)}
        self.assertEqual(expected,set(new['enabledCells']))
        for x in (233,234):self.assertEqual(16,new['collision'][159*new['width']+x])
        self.assertFalse(any(new['collision'][i]==1 and i not in old['enabledCells']for i in new['enabledCells']))
    def test_missing_groups_fake_values_wrong_graphics_and_unrelated_capabilities_rejected(self):
        for kind in ('group','gate','hp','graphic','capability'):
            p=copy.deepcopy(self.p)
            if kind=='group':p['combatOverlay']['zones'][0]['groups'].pop()
            elif kind=='gate':p['combatOverlay']['zones'][0]['randomThreshold']=0
            elif kind=='hp':p['combatOverlay']['enemies'][0]['hp']=1
            elif kind=='graphic':p['combatOverlay']['graphics'][0]['origin'][1]+=8
            else:p['sceneCapabilityUpdates'][0]['implementedCapabilities'].append('FLY')
            with self.assertRaises(ValueError,msg=kind):self.changed(p)
    def test_original_cpu_proof_bytes_are_mandatory(self):
        path=ci.ROOT/'game-data/provenance/world-continent-bridges.json';proof=json.loads(path.read_text(encoding='utf-8'))
        def load(p):return proof if Path(p).resolve()==path.resolve()else json.loads(Path(p).read_text(encoding='utf-8'))
        for field in ('activeCpuSha256','cpuExpectedSha256'):
            old=proof[field];proof[field]='0'*64
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError):ex.validate_continent_foot_bridges(self.r,str(path.relative_to(ci.ROOT)))
            proof[field]=old

if __name__=='__main__':unittest.main()
