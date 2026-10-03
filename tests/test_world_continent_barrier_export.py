"""Original six actor flags remain conditions, never silently deleted as UX boundaries."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_npcs,digest

class ContinentBarrierExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-continent-barrier-content.json';cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent);cls.result=ex.export_from_base(cls.base,cls.path,cls.pin)
        cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_exact_six_original_records_and_preserved_bridge_collision_and_media(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin));self.assertEqual(195,len(self.result))
        self.assertEqual(self.pin['manifestSha256'],digest(self.result['manifest.json']))
        scene=json.loads(self.result['scene.json']);barriers=[b for b in scene['sceneBarriers']if b['mapId']==16]
        self.assertEqual(6,len(barriers));records=extract_npcs(self.r,16)['records']
        for b,n in zip(barriers,records):
            raw=bytes.fromhex(n['rawHex']);self.assertEqual(n['range'],b['recordSource']);self.assertEqual(f'rom.map.16.flag.{raw[13]}',b['removedFlagId'])
        old=json.loads(self.old['scene16.json']);new=json.loads(self.result['scene16.json'])
        for k in ('grid','collision','walkableClasses','enabledCells','sourceEdges','targetEdges','unavailableRegions'):self.assertEqual(old.get(k),new.get(k),k)
        self.assertEqual({b['cell'][1]*new['width']+b['cell'][0]for b in barriers}|set(old.get('dynamicObjectCells',[])),set(new['dynamicObjectCells']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        self.assertEqual(json.loads(self.old['combat.json'])['zones'],json.loads(self.result['combat.json'])['zones'])
    def test_wrong_flag_cell_sprite_and_actor_identity_cannot_open_original_gate(self):
        for kind in ('flag','cell','id','map','record'):
            p=copy.deepcopy(self.p);b=p['sceneBarriers'][0]
            if kind=='flag':b['removedFlagId']='rom.map.16.flag.0'
            elif kind=='cell':b['cell'][0]+=1
            elif kind=='id':b['id']='invented'
            elif kind=='map':b['mapId']=0
            else:b['recordSource']=p['sceneBarriers'][1]['recordSource']
            with self.assertRaises(ValueError,msg=kind):self.changed(p)
    def test_original_cpu_filter_table_and_scoped_routine_are_required(self):
        path=ci.ROOT/'game-data/provenance/world-continent-actor-barriers.json';proof=json.loads(path.read_text(encoding='utf-8'))
        def load(p):return proof if Path(p).resolve()==path.resolve()else json.loads(Path(p).read_text(encoding='utf-8'))
        for field in ('flagAddress','cpuExpectedSha256'):
            old=proof[field];proof[field]=0 if isinstance(old,int)else'0'*64
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
            proof[field]=old

if __name__=='__main__':unittest.main()
