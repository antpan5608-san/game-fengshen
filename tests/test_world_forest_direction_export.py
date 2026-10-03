"""Actual direction oracle, unchanged media and legal forest continuation."""
import collections,copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex

class ForestDirectionExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-forest-direction-content.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-forest101-direction-content.json'
        cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin)
    def test_direction_correction_retains_grid_inventory_groups_and_all_media(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(199,len(self.result))
        old=json.loads(self.old['scene101.json']);new=json.loads(self.result['scene101.json'])
        for key in ('grid','collision','enabledCells','dynamicObjectCells','transitionCells','walkableClasses','sourceEdges'):
            self.assertEqual(old[key],new[key],key)
        self.assertEqual({'3':['LEFT','RIGHT'],'7':['LEFT','RIGHT']},new['targetEdges'])
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        for name in ('combat.json','scene.json'):
            a=json.loads(self.old[name]);b=json.loads(self.result[name]);a['version']=b['version']
            self.assertEqual(a,b,name)
        self.assertEqual(self.pin['manifestSha256'],ex.digest(self.result['manifest.json']))
    def test_corrected_edges_still_allow_real_entry_to_room_approach(self):
        s=json.loads(self.result['scene101.json']);w=s['width'];start=(8,51);goal=(32,13)
        queue=collections.deque([start]);seen={start};enabled=set(s['enabledCells'])
        while queue:
            x,y=queue.popleft()
            if(x,y)==goal:break
            source=s['collision'][y*w+x]
            for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
                point=(x+dx,y+dy);nx,ny=point;index=ny*w+nx
                if not(0<=nx<w and 0<=ny<s['height'])or point in seen or index not in enabled:continue
                target=s['collision'][index]
                if key in s['sourceEdges'].get(str(source),[])or key in s['targetEdges'].get(str(target),[]):continue
                seen.add(point);queue.append(point)
        self.assertIn(goal,seen,'No fake bridge or shortcut to preserve a test route')
        self.assertEqual((0,3),(s['collision'][19*w+26],s['collision'][19*w+27]))
        self.assertIn('RIGHT',s['targetEdges']['3'])
    def test_wrong_source_direction_or_cpu_table_is_rejected(self):
        proof_path=ci.ROOT/'game-data/provenance/world-forest101-direction.json'
        original=json.loads(proof_path.read_text(encoding='utf-8'))
        for kind in ('direction','cpu','span'):
            value=copy.deepcopy(original)
            if kind=='direction':value['targetEdges']={}
            elif kind=='cpu':value['cpuExpectedSha256']='0'*64
            else:value['sources'][0]['sha256']='0'*64
            def load(path):
                return value if Path(path).resolve()==proof_path.resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=kind):
                ex.export_from_base(self.base,self.path,self.pin,verify_target=False)

if __name__=='__main__':unittest.main()
