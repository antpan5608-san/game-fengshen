"""Scoped original medical room/commands; local export is not normal App acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_map,digest

class ClinicExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-clinic-content.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-clinic-content.json';cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.scene=json.loads(cls.result['scene.json']);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_repeatable_parent_recipe_original_media_and_exact_manifest(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(192,len(self.result));self.assertEqual(33,len(self.scene['maps']))
        self.assertEqual(self.pin['manifestSha256'],digest(self.result['manifest.json']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        self.assertEqual(json.loads(self.old['combat.json'])['enemies'],json.loads(self.result['combat.json'])['enemies'])
        ci.validate_item_sources(self.result)
    def test_real_room_directionless_bed_edges_shared_caller_and_actual_commands(self):
        room=json.loads(self.result['scene20.json']);original=extract_map(self.r,20)
        self.assertEqual((16,15,2),(room['width'],room['height'],original['tilesetId']))
        self.assertEqual([t for row in original['grid']for t in row],room['grid'])
        self.assertEqual([0,2,5],room['walkableClasses']);self.assertNotIn('sourceEdges',room);self.assertNotIn('targetEdges',room)
        self.assertEqual(6,len(self.scene['clinics']))
        for caller,door in [(1,[8,10]),(2,[22,26]),(3,[7,18])]:
            e=next(e for e in self.scene['exits']if e['fromMapId']==caller and e['toMapId']==20)
            self.assertEqual(door,e['trigger']);self.assertEqual([7,12],e['spawn']);self.assertTrue(e['captureCaller'])
            self.assertEqual(2,len([b for b in self.scene['serviceBindings']if b['callerMapId']==caller and b['interiorMapId']==20]))
        e=next(e for e in self.scene['exits']if e['fromMapId']==20);self.assertTrue(e['returnToCaller'])
        self.assertEqual([7,12,254,0,0],list(ex.checked_span(self.r,e['source'])))
        for c in self.scene['clinics']:ex.validate_world_clinic_definition(self.r,c)
    def test_wrong_effect_price_target_door_and_town_profile_are_rejected(self):
        for case in ['free','full-heal','all-flags','third-treatment','npc','door','town-edges','wall']:
            p=copy.deepcopy(self.p)
            revival=next(c for c in p['clinics']if c['kind']=='REVIVAL');care=next(c for c in p['clinics']if c['kind']=='TREATMENT')
            if case=='free':care['treatments'][0]['price']=0
            elif case=='full-heal':revival['recoveredHp']=128
            elif case=='all-flags':care['treatments'][0]['statusMask']=255
            elif case=='third-treatment':care['treatments'].append(copy.deepcopy(care['treatments'][0]))
            elif case=='npc':p['npcs'][0]['cell'][0]-=1
            elif case=='door':p['exits'][0]['trigger'][0]-=1
            elif case=='town-edges':p['maps'][0]['directionalCollision']=True
            else:p['maps'][0]['walkableClasses'].append(1)
            with self.assertRaises(ValueError,msg=case):self.changed(p)
    def test_original_active_prg_and_expected_table_hashes_are_required(self):
        path=ci.ROOT/'game-data/provenance/world-clinic-rules.json';proof=json.loads(path.read_text(encoding='utf-8'))
        def load(p):return proof if Path(p).resolve()==path.resolve()else json.loads(Path(p).read_text(encoding='utf-8'))
        for field in ['activeCpuSha256','revivalExpectedSha256','careExpectedSha256']:
            old=proof[field];proof[field]='0'*64
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError):ex.validate_world_clinic_definition(self.r,self.scene['clinics'][0])
            proof[field]=old

if __name__=='__main__':unittest.main()
