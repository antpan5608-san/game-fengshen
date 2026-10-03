"""Scoped village4 bridges/services/font rules; normal Android is a separate gate."""
import collections,copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_world_service_catalog

class VillageFourExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.path='game-data/provenance/world-village4-content.json';cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.pin=copy.deepcopy(cls.parent);cls.pin['contentVersion']='opening-segment-001-c40';cls.pin['iteration']['provenance']=cls.path
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False);cls.pin['manifestSha256']=ci.sha(cls.result['manifest.json'])
        cls.scene=json.loads(cls.result['scene.json']);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_repeatable_local_parent_and_original_media_bytes(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(220,len(self.result));self.assertEqual(40,len(self.scene['maps']));ci.validate_item_sources(self.result)
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        proof=ex.validate_world_village4_resources(self.r)
        self.assertEqual(8,len([n for n in self.scene['npcs']if n['mapId']==4]))
        self.assertEqual('『我把船借給你們。』',next(d for d in self.scene['dialogues']if d['id']=='rom.dialogue.14.15')['text'])
        self.assertEqual(14,proof['talk']['textGroup'])
    def test_original_stock_prices_inn_and_caller_return(self):
        c=extract_world_service_catalog(self.r)
        for room,category in [(17,'weapon'),(18,'armor'),(19,'medicine')]:
            shop=next(s for s in self.scene['shops']if s['id']==f'rom.shop.4.{room}')
            stock=next(s for s in c['stocks']if(s['category'],s['contextIndex'])==(category,4))
            self.assertEqual([f'rom.{category}.{i}'for i in stock['originalIds']],shop['items']);self.assertEqual(shop['items'],shop['sellItems'])
        inn=next(s for s in self.scene['inns']if s['id']=='rom.inn.4');self.assertEqual((90,114),(inn['price'],inn['blockedStatusMask']))
        self.assertEqual(2,len([c for c in self.scene['clinics']if c['callerMapId']==4]))
        self.assertEqual(6,len([b for b in self.scene['serviceBindings']if b['callerMapId']==4]))
        back=next(e for e in self.scene['exits']if e['fromMapId']==4 and e['toMapId']==16)
        self.assertEqual(([15,29],[146,150],'DOWN'),(back['trigger'],back['spawn'],back['direction']))
        self.assertEqual([255,150,16,146,150],list(ex.checked_span(self.r,back['source'])))
        boat=next(o for o in self.scene['mapObjects']if o['id']=='rom.object.4.0');self.assertEqual('NOT_IMPLEMENTED',boat['interaction'])
        lender=next(n for n in self.scene['npcs']if n['id']=='rom.npc.4.8');self.assertNotIn('originalTalk',lender)
        self.assertEqual([],lender['firstEffects'])
    def test_bridge_profile_reaches_actual_shared_service_doors_without_opening_walls(self):
        m=json.loads(self.result['scene4.json']);w=m['width'];source=m['sourceEdges'];target=m['targetEdges']
        self.assertEqual(['UP','DOWN'],source['10']);self.assertEqual(['LEFT','RIGHT'],source['11']);self.assertNotIn('10',target)
        q=collections.deque([(15,29)]);seen={(15,29)}
        while q:
            x,y=q.popleft();sc=m['collision'][y*w+x]
            for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
                nx,ny=x+dx,y+dy
                if not(0<=nx<w and 0<=ny<m['height']):continue
                idx=ny*w+nx;tc=m['collision'][idx]
                if idx not in m['enabledCells']or idx in m['dynamicObjectCells']or key in source.get(str(sc),[])or key in target.get(str(tc),[]):continue
                if (nx,ny)not in seen:seen.add((nx,ny));q.append((nx,ny))
        for cell in [(27,23),(23,9),(14,11),(8,22),(16,18),(13,3)]:self.assertIn(cell,seen,cell)
        self.assertNotIn((10,3),seen);self.assertEqual(1,m['collision'][3*w+10])
    def test_normal_driver_adjacent_npcs_and_world_return_use_actual_passable_cells(self):
        m=json.loads(self.result['scene4.json']);w=m['width'];blocked=set(m['dynamicObjectCells'])
        def component(start):
            seen={start};q=collections.deque([start])
            while q:
                x,y=q.popleft();sc=m['collision'][y*w+x]
                for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
                    nx,ny=x+dx,y+dy;i=ny*w+nx
                    if not(0<=nx<w and 0<=ny<m['height'])or i not in m['enabledCells']or i in blocked:continue
                    if key in m['sourceEdges'].get(str(sc),[])or key in m['targetEdges'].get(str(m['collision'][i]),[]):continue
                    if(nx,ny)not in seen:seen.add((nx,ny));q.append((nx,ny))
            return seen
        seen=component((15,29))
        for npc in [n for n in self.scene['npcs']if n['mapId']==4]:
            x,y=npc['cell'];at=next((p for p in [(x,y+1),(x+1,y),(x-1,y),(x,y-1)]if p in seen),None)
            self.assertIn(at,seen,npc['id'])
        self.assertIn(14*w+13,m['enabledCells']);self.assertNotIn((13,14),seen)
        self.assertIn((14,13),seen)  # NPC5 is reached from the actual eastern bank.
        world=json.loads(self.result['scene16.json']);wi=151*world['width']+146
        self.assertIn(wi,world['enabledCells']);self.assertNotIn(wi,world['dynamicObjectCells'])
        self.assertEqual(2,world['collision'][wi])
    def test_wrong_bridge_npc_flag_text_stock_cost_or_return_is_rejected(self):
        for kind in ['bridge','npc','stock','price','return']:
            p=copy.deepcopy(self.p)
            if kind=='bridge':p['maps'][0]['walkableClasses'].append(1)
            elif kind=='npc':p['npcs'][0]['originalTalk']['witnessFlagId']='invented.before.travel'
            elif kind=='stock':p['shops'][0]['items'].pop()
            elif kind=='price':p['inns'][0]['price']=0
            else:next(e for e in p['exits']if e['fromMapId']==4)['spawn'][0]=147
            with self.assertRaises(ValueError,msg=kind):self.changed(p)

if __name__=='__main__':unittest.main()
