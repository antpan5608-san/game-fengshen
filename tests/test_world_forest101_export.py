"""Scoped forest input, original complete encounters and existing export path."""
import collections,copy,json,os,sys,unittest
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

    def test_east_supply_requires_real_sea_and_cave_exits(self):
        """Static route guard for v53's real failure; not normal App evidence."""
        world=json.loads(self.result['scene.json'])
        maps={m['id']:json.loads(self.result[m['scene']])for m in world['maps']}
        # These are prerequisites already asserted at the same-candidate cave
        # continuation load, not flags granted by this route or the UI.
        flags={'rom.map.25.flag.1':True,'rom.map.25.flag.128':True,
               'rom.map.85.flag.128':True}
        def reachable(mid,start,target):
            scene=maps[mid];width=scene['width'];enabled=set(scene['enabledCells'])
            transitions=set(scene['transitionCells']);blocked=set(scene.get('dynamicObjectCells',[]))
            for obj in world['mapObjects']:
                rule=obj.get('itemTarget')
                if obj['mapId']==mid and rule and flags.get(rule['removedFlagId']):
                    x,y=obj['cell'];blocked.discard(y*width+x)
            for rule in world['sceneBarriers']:
                if rule['mapId']==mid and flags.get(rule['removedFlagId']):
                    x,y=rule['cell'];blocked.discard(y*width+x)
            doors={tuple(e['trigger'])for e in world['exits']if e['fromMapId']==mid and not e.get('direction')}
            queue=collections.deque([start]);seen={start}
            while queue:
                x,y=queue.popleft()
                if (x,y)==target:return True
                source=scene['collision'][y*width+x]
                for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
                    point=(x+dx,y+dy);nx,ny=point;index=ny*width+nx
                    if not(0<=nx<width and 0<=ny<scene['height'])or point in seen:continue
                    category=scene['collision'][index]
                    if index not in enabled or index in blocked:continue
                    if category not in scene['walkableClasses']and index not in transitions:continue
                    if key in scene.get('sourceEdges',{}).get(str(source),[])or key in scene.get('targetEdges',{}).get(str(category),[]):continue
                    if point!=target and point in doors:continue
                    seen.add(point);queue.append(point)
            return False
        self.assertFalse(reachable(16,(191,102),(214,110)))
        legs=[(16,(191,102),(186,102),25,(26,14)),
              (25,(26,14),(53,30),16,(213,118)),
              (16,(213,118),(212,114),85,(30,29)),
              (85,(30,29),(2,2),16,(215,106)),
              (16,(215,106),(214,110),25,(54,22)),
              (25,(54,22),(49,21),95,(13,29))]
        for mid,start,target,next_map,spawn in legs:
            self.assertTrue(reachable(mid,start,target),(mid,start,target))
            matching=[e for e in world['exits']if e['fromMapId']==mid and tuple(e['trigger'])==target]
            self.assertEqual(1,len(matching))
            self.assertEqual((next_map,spawn),(matching[0]['toMapId'],tuple(matching[0]['spawn'])))
        flags.pop('rom.map.25.flag.1')
        self.assertFalse(reachable(25,(26,14),(53,30)))

if __name__=='__main__':unittest.main()
