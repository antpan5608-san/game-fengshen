"""Scoped original item route/export. CPU and static geometry are not App proof."""
import copy,json,os,sys,unittest,collections
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_encounter_groups

class FiveDragonExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path='game-data/provenance/world-five-dragon-content.json'
        cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.pin=copy.deepcopy(cls.parent);cls.pin['contentVersion']='opening-segment-001-c43';cls.pin['iteration']['provenance']=cls.path
        cls.base=ci.content(Path(os.environ['FENGSHEN_CONTENT_BASE_APK']),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False)
        cls.pin['manifestSha256']=ci.sha(cls.result['manifest.json']);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_repeated_export_exact_manifest_and_previous_media_preserved(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(242,len(self.result));self.assertEqual(46,len(json.loads(self.result['scene.json'])['maps']))
        ci.validate_item_sources(self.result)
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
    def test_independent_doors_full_original_groups_and_existing_medicine_reused(self):
        scene=json.loads(self.result['scene.json']);combat=json.loads(self.result['combat.json'])
        actual={(e['fromMapId'],*e['trigger'],e['toMapId'],*e['spawn'])for e in scene['exits']if e['fromMapId']in [99,163]or e['toMapId']in [99,163]}
        self.assertEqual({(16,183,143,99,8,51),(99,8,51,16,183,143),(99,13,11,163,7,14),(163,7,14,99,13,11)},actual)
        for zone,mid in [(20,16),(21,99)]:
            z=next(z for z in combat['zones']if z['id']==zone and z['mapId']==mid)
            self.assertEqual(extract_encounter_groups(self.r,zone)['groups'],z['groups'])
            self.assertEqual('LOW'if mid==16 else'HIGH',z['randomGate'])
        z=next(z for z in combat['zones']if z['id']==20 and z['mapId']==16)
        self.assertEqual([[180,136,191,151],[145,125,179,147]],z['rectangles'])
        self.assertEqual(bytes([20,180,136,191,151,145,125,179,147,0]),self.r.read(11,0xc164,10))
        old=json.loads(self.old['scene.json'])
        self.assertEqual(next(i for i in old['items']if i['id']=='rom.medicine.9'),next(i for i in scene['items']if i['id']=='rom.medicine.9'))
        for n in scene['npcs']:
            if n['mapId']==99:ex.validate_world_chest_grant(self.r,n)
        self.assertEqual(3,len([n for n in scene['npcs']if n['mapId']==99]))
    def test_teacher_gift_and_real_special_command_not_map_heal_or_invented_unlock(self):
        proof=ex.validate_world_teacher163_binding(self.r);scene=json.loads(self.result['scene.json']);combat=json.loads(self.result['combat.json'])
        self.assertEqual(proof['items'][0],next(i for i in scene['items']if i['id']=='rom.special.9'))
        self.assertTrue(proof['rules']['flagBeforeGift']);self.assertTrue(proof['rules']['fullCategoryRetainsFlag'])
        use=proof['items'][0]['battleBindingUse'];self.assertTrue(use['reusable']);self.assertTrue(use['consumesAction']);self.assertFalse(use['chooseTarget'])
        self.assertNotIn('herbUse',proof['items'][0]);self.assertNotIn('buyPrice',proof['items'][0])
        boss=next(b for b in combat['bosses']if b.get('mapId')==76)
        self.assertNotIn('requiredInventory',boss)
        self.assertEqual(proof['npcs'],[n for n in scene['npcs']if n['mapId']==163])
    def test_normal_gift_route_uses_real_foot_edges_and_does_not_cross_unrelated_doors(self):
        world=json.loads(self.result['scene.json'])
        def reachable(mid,start,goal,context=True):
            m=json.loads(self.result[f'scene{mid}.json']);w=m['width'];h=m['height']
            objects=set(m['dynamicObjectCells'])
            if mid==163 and context:
                # Route after the original three-party island-load selector;
                # the base pre-island guard still blocks and is tested below.
                npc=next(n for n in world['npcs']if n['id']=='rom.npc.163.0');variant=npc['stateVariant']
                objects.discard(npc['cell'][1]*w+npc['cell'][0]);objects.add(variant['cell'][1]*w+variant['cell'][0])
            allowed=set(m['enabledCells'])-objects
            exits={tuple(e['trigger'])for e in world['exits']if e['fromMapId']==mid and 'edgeDirection'not in e}
            queue=collections.deque([start]);seen={start}
            while queue:
                x,y=queue.popleft()
                if(x,y)==goal:return True
                for key,nx,ny in [('UP',x,y-1),('DOWN',x,y+1),('LEFT',x-1,y),('RIGHT',x+1,y)]:
                    if not(0<=nx<w and 0<=ny<h)or ny*w+nx not in allowed:continue
                    if (nx,ny)in seen or((nx,ny)in exits and(nx,ny)!=goal):continue
                    source=m['collision'][y*w+x];target=m['collision'][ny*w+nx]
                    if key in m.get('sourceEdges',{}).get(str(source),[])or key in m.get('targetEdges',{}).get(str(target),[]):continue
                    seen.add((nx,ny));queue.append((nx,ny))
            return False
        for mid,start,goal in [(16,(146,150),(183,143)),(16,(183,143),(146,150)),
                (99,(8,51),(13,11)),(99,(13,11),(8,51)),(163,(7,14),(7,5)),(163,(7,5),(7,14))]:
            self.assertTrue(reachable(mid,start,goal),(mid,start,goal))
        self.assertFalse(reachable(163,(7,14),(7,5),context=False))
        ex.validate_world_teacher163_gate(self.r)
        m=json.loads(self.result['scene163.json'])
        self.assertEqual(1,m['collision'][4*m['width']+7]);self.assertNotIn(4*m['width']+7,m['enabledCells'])
    def test_guessed_price_target_heal_map_walls_or_npc_effect_rejected(self):
        for change in [lambda p:p['items'][0].update(buyPrice=1),
                lambda p:p['items'][0]['battleBindingUse'].update(chooseTarget=True),
                lambda p:p['items'][0].update(herbUse={'healHp':50}),
                lambda p:p['maps'][1].update(walkableClasses=[0,1,2]),
                lambda p:p['npcs'][1].update(firstEffects=[{'money':100}])]:
            p=copy.deepcopy(self.p);change(p)
            with self.assertRaises(ValueError):self.changed(p)
    def test_wrong_parent_and_original_door_rejected(self):
        for change in [lambda p:p['baseExport'].update(pinSha256='0'*64),lambda p:p['exits'][0].update(spawn=[1,1])]:
            p=copy.deepcopy(self.p);change(p)
            with self.assertRaises(ValueError):self.changed(p)

if __name__=='__main__':unittest.main()
