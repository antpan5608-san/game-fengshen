"""Fixed original ferry/zone22, not unrestricted sea travel or normal App proof."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_encounter_groups,extract_enemy_single_special_base

class FerryExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path='game-data/provenance/world-ferry-content.json'
        cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.pin=copy.deepcopy(cls.parent);cls.pin.update(contentVersion='opening-segment-001-c41')
        cls.pin['iteration']['provenance']=cls.path
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False)
        cls.pin['manifestSha256']=ci.sha(cls.result['manifest.json']);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_reproducible_fixed_parent_and_unchanged_media(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(225,len(self.result));self.assertEqual(41,len(json.loads(self.result['scene.json'])['maps']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        ci.validate_item_sources(self.result)
    def test_exact_fixed_route_existing_boat_and_not_free_water(self):
        scene=json.loads(self.result['scene.json']);proof=ex.validate_world_ferry_resources(self.r)
        self.assertEqual(proof['rules'],scene['ferries']);self.assertEqual([18,18],[len(v['legs'])for v in scene['ferries']])
        obj=next(o for o in scene['mapObjects']if o['id']=='rom.object.4.0')
        self.assertEqual(('FERRY_CONTACT','rom.ferry.45',[10,3]),(obj['interaction'],obj['ferryId'],obj['cell']))
        old=json.loads(self.old['scene16.json']);new=json.loads(self.result['scene16.json'])
        self.assertEqual(old['collision'],new['collision']);self.assertEqual(old['grid'],new['grid'])
        self.assertEqual({128*old['width']+153},set(new['enabledCells'])-set(old['enabledCells']))
        self.assertEqual({128*old['width']+153},set(new['transitionCells'])-set(old['transitionCells']))
        for key in old:
            if key not in ('enabledCells','transitionCells','version'):self.assertEqual(old[key],new[key],key)
        old4=json.loads(self.old['scene4.json']);new4=json.loads(self.result['scene4.json'])
        old4['version']=new4['version'];self.assertEqual(old4,new4)
        cave=json.loads(self.result['scene79.json']);self.assertEqual([0],cave['walkableClasses'])
        self.assertNotIn('terrain',cave)
    def test_complete_actual_zone_and_existing_special_behavior(self):
        combat=json.loads(self.result['combat.json']);z=next(v for v in combat['zones']if v['mapId']==79)
        self.assertEqual((22,'HIGH',245,11),(z['id'],z['randomGate'],z['randomThreshold'],len(z['groups'])))
        self.assertEqual(extract_encounter_groups(self.r,22)['groups'],z['groups'])
        enemy=next(e for e in combat['enemies']if e['id']==46)
        for k,v in extract_enemy_single_special_base(self.r,46).items():self.assertEqual(v,enemy[k])
        self.assertEqual('UNKNOWN',enemy['nameConfidence'])
    def test_guessed_route_cost_missing_enemy_or_free_sea_rejected(self):
        for kind in ['route','boat','graphic','groups','enemy','gate','wall','return']:
            p=copy.deepcopy(self.p)
            if kind=='route':p['ferries'][0]['legs'][0]['x']+=1
            elif kind=='boat':p['existingObjectInteractionUpdates'][0]['id']='rom.npc.4.8'
            elif kind=='graphic':p['graphics']['actor-218-ferry.png']['rgbaSha256']='0'*64
            elif kind=='groups':p['combatOverlay']['zones'][0]['groups'].pop()
            elif kind=='enemy':p['combatOverlay']['enemies'][0]['specialBaseDamage']=0
            elif kind=='gate':p['combatOverlay']['zones'][0]['randomThreshold']=0
            elif kind=='wall':p['maps'][0]['walkableClasses'].append(1)
            else:p['exits'][1]['spawn'][0]+=1
            with self.assertRaises(ValueError,msg=kind):self.changed(p)

if __name__=='__main__':unittest.main()
