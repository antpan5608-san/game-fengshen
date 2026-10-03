"""Static original hall topology guards; not normal Android acceptance."""
import collections,json,os,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex

class HellRouteDriverTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pin=json.loads((ci.ROOT/'ci/golden-world-forest101-content.json').read_text(encoding='utf-8'))
        base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),pin['iteration']['base'])
        result=ex.export_from_base(base,pin['iteration']['provenance'],pin)
        cls.result=result
        cls.world=json.loads(result['scene.json'])
        cls.maps={m['id']:json.loads(result[m['scene']])for m in cls.world['maps']}
        cls.halls=[70,60,61,62,63,64,65,66,67,68]
    def route(self,start,target,completed):
        """Isolated completed-gate fixture, never player flag mutation."""
        allowed={23,*completed};q=collections.deque([start]);parents={start:(start,None)}
        blockers={mid:set(self.maps[mid].get('dynamicObjectCells',[]))for mid in allowed}
        for b in self.world['sceneBarriers']:
            if b['mapId'] in completed:blockers[b['mapId']].discard(b['cell'][1]*self.maps[b['mapId']]['width']+b['cell'][0])
        exits={(e['fromMapId'],*e['trigger']):e for e in self.world['exits']if not e.get('direction')}
        edges={2:['LEFT'],3:['RIGHT'],4:['UP','LEFT'],5:['DOWN','LEFT'],6:['UP'],7:['DOWN'],8:['UP','RIGHT'],9:['DOWN','RIGHT'],10:['UP','DOWN'],11:['LEFT','RIGHT']}
        while q:
            at=q.popleft();mid,x,y=at;m=self.maps[mid];w=m['width'];source=m['collision'][y*w+x]
            if at==target:
                result=[]
                while at!=start:
                    at,e=parents[at]
                    if e:result.append((e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn'])))
                return result[::-1]
            for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
                nx,ny=x+dx,y+dy;i=ny*w+nx
                if not(0<=nx<w and 0<=ny<m['height'])or i not in m['enabledCells']or i in blockers[mid]:continue
                if key in edges.get(source,[])or m['collision'][i] in [1,14]:continue
                e=exits.get((mid,nx,ny));next_at=(mid,nx,ny)
                if next_at!=target and e:
                    if e['toMapId'] not in allowed:continue
                    next_at=(e['toMapId'],*e['spawn'])
                    tm=self.maps[next_at[0]];ti=next_at[2]*tm['width']+next_at[1]
                    if ti in blockers[next_at[0]]or ti not in tm['enabledCells']:continue
                else:e=None
                if next_at in parents:continue
                parents[next_at]=(at,e);q.append(next_at)
        return None
    def test_village_cannot_reach_later_partitions_without_original_halls(self):
        self.assertIsNone(self.route((23,55,91),(23,55,70),[]))
        self.assertIsNone(self.route((23,55,91),(23,55,35),[]))
        self.assertEqual([],self.route((23,55,91),(23,28,80),[]))
    def test_normal_sequence_has_each_real_next_door_without_a_reverse_supply_assumption(self):
        start=(23,55,91)
        for mid in self.halls:
            m=self.maps[mid]
            entry=next(e for e in self.world['exits']if e['fromMapId']==23 and e['toMapId']==mid and e['spawn']==m['spawn'])
            self.assertEqual([],self.route(start,(23,*entry['trigger']),[]),(mid,start))
            gate=next(b for b in self.world['sceneBarriers']if b['mapId']==mid)
            exit=next(e for e in self.world['exits']if e['fromMapId']==mid and e['trigger']==gate['cell'])
            start=(23,*exit['spawn'])
        # Original source11 forbids lateral departure at the lower bridge;
        # a reciprocal hall exit alone does not prove a full village return.
        self.assertIsNone(self.route((23,28,80),(23,55,91),[]))
    def test_village_return_and_first_training_cells_are_real_connected_ground(self):
        m=self.maps[23];w=m['width']
        self.assertEqual(1,m['collision'][92*w+55])
        self.assertEqual(1,m['collision'][93*w+55])
        for point in [(23,54,91),(23,54,93),(23,54,94)]:
            self.assertEqual(0,m['collision'][point[2]*w+point[1]])
            self.assertIn(point[2]*w+point[1],m['enabledCells'])
            self.assertEqual([],self.route((23,55,91),point,[]))
        self.assertEqual([],self.route((23,54,94),(23,54,93),[]))
        self.assertEqual([],self.route((23,54,91),(23,55,91),[]))
        zone=next(z for z in json.loads(self.result['combat.json'])['zones']if z['mapId']==23 and z['id']==8)
        for x,y in [(54,93),(54,94)]:
            self.assertTrue(any(l<x<=r and t<y<=b for l,t,r,b in zone['rectangles']))
    def test_later_encounters_are_reachable_from_their_actual_completed_hall_landings(self):
        for mid,target in [(61,(23,50,60)),(65,(23,51,32))]:
            gate=next(b for b in self.world['sceneBarriers']if b['mapId']==mid)
            exit=next(e for e in self.world['exits']if e['fromMapId']==mid and e['trigger']==gate['cell'])
            self.assertEqual([],self.route((23,*exit['spawn']),target,[]))
            adjacent=(target[0],target[1],target[2]+1)
            self.assertEqual([],self.route(target,adjacent,[]))
            self.assertEqual([],self.route(adjacent,target,[]))
            zone=next(z for z in json.loads(self.result['combat.json'])['zones']if z['mapId']==23 and z['id']==(10 if mid==61 else 13))
            for point in (target,adjacent):self.assertTrue(any(l<point[1]<=r and t<point[2]<=b for l,t,r,b in zone['rectangles']))

if __name__=='__main__':unittest.main()
