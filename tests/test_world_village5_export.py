"""Original town5 caller data and shared profile; no Android PASS from export."""
import collections,copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_world_service_catalog
class Village5ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path='game-data/provenance/world-village5-content.json';cls.p=ex.load(ci.ROOT/cls.path)
  cls.parent=ex.load(ci.ROOT/cls.p['baseExport']['pinPath']);cls.pin=copy.deepcopy(cls.parent)
  cls.pin['contentVersion']='opening-segment-001-c44';cls.pin['iteration']['provenance']=cls.path
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.result=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False);cls.pin['manifestSha256']=ci.sha(cls.result['manifest.json'])
  cls.scene=json.loads(cls.result['scene.json']);cls.r=ex.iteration_reader()
 def changed(self,p):
  def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
  with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
 def test_repeatable_exact_parent_media_and_full_manifest(self):
  self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
  self.assertEqual((248,47),(len(self.result),len(self.scene['maps'])));ci.validate_item_sources(self.result)
  for name,raw in self.old.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
  self.assertEqual(self.p['baseManifestSha256'],ci.sha(self.old['manifest.json']))
 def test_three_original_stocks_prices_and_original_return_rows(self):
  catalog=extract_world_service_catalog(self.r)
  for room,cat,stock in [(17,'weapon',[8,23,35]),(18,'armor',[5,13,19,30,39]),(19,'medicine',[1,2,4,10,12,13,14])]:
   shop=next(s for s in self.scene['shops']if s['id']==f'rom.shop.5.{room}')
   self.assertEqual([f'rom.{cat}.{i}'for i in stock],shop['items']);self.assertEqual(shop['items'],shop['sellItems'])
  inn=next(i for i in self.scene['inns']if i['id']=='rom.inn.5');self.assertEqual(150,inn['price'])
  self.assertEqual(6,len([b for b in self.scene['serviceBindings']if b['callerMapId']==5]))
  self.assertEqual(2,len([c for c in self.scene['clinics']if c['callerMapId']==5]))
  rows=[e for e in self.scene['exits']if e['fromMapId']==5 and e['toMapId']==16]
  self.assertEqual([[14,29],[15,29],[16,29]],[e['trigger']for e in rows])
  for e in rows:
   self.assertEqual([107,157],e['spawn']);self.assertNotIn('triggerMode',e)
   self.assertEqual(e['trigger']+[16,107,157],list(ex.checked_span(self.r,e['source'])))
 def test_original_profile_reaches_five_service_doors_without_opening_walls(self):
  m=json.loads(self.result['scene5.json']);w=m['width'];seen={(15,28)};q=collections.deque(seen)
  while q:
   x,y=q.popleft();sc=m['collision'][y*w+x]
   for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
    nx,ny=x+dx,y+dy;i=ny*w+nx
    if not(0<=nx<w and 0<=ny<m['height'])or i not in m['enabledCells']or i in m['dynamicObjectCells']:continue
    if key in m['sourceEdges'].get(str(sc),[])or key in m['targetEdges'].get(str(m['collision'][i]),[]):continue
    if(nx,ny)not in seen:seen.add((nx,ny));q.append((nx,ny))
  for cell in [(9,16),(6,16),(6,22),(21,23),(27,23)]:self.assertIn(cell,seen,cell)
  self.assertNotIn((0,0),seen);self.assertEqual(1,m['collision'][0])
  self.assertNotIn(0,m['enabledCells'])
  self.assertEqual(['UP','DOWN'],m['sourceEdges']['10']);self.assertEqual(['LEFT','RIGHT'],m['sourceEdges']['11'])
 def test_real_ordinary_npcs_do_not_grant_or_add_plot_gate(self):
  proof=ex.validate_world_village5_resources(self.r)
  npcs=[n for n in self.scene['npcs']if n['mapId']==5];self.assertEqual(5,len(npcs))
  self.assertEqual([163,142,162,161,142],[n['spriteId']for n in npcs])
  for n in npcs:self.assertEqual([],n['firstEffects']);self.assertNotIn('originalTalk',n)
  self.assertEqual('『這裏是佳東鎮。』',next(d for d in proof['dialogues']if d['id']=='rom.dialogue.15.6')['text'])
  self.assertFalse(any(n['spriteId']==198 for n in npcs))
 def test_wrong_rule_price_stock_actor_text_or_return_rejected(self):
  for kind in ['walk','price','stock','npc','return','text']:
   p=copy.deepcopy(self.p)
   if kind=='walk':p['maps'][0]['walkableClasses'].append(1)
   elif kind=='price':p['inns'][0]['price']=1
   elif kind=='stock':p['shops'][0]['items'].pop()
   elif kind=='npc':p['npcs'][0]['firstEffects']=[{'money':100}]
   elif kind=='return':next(e for e in p['exits']if e['fromMapId']==5 and e['toMapId']==16)['spawn'][0]=108
   else:p['dialogues'][0]['text']='伪造剧情'
   with self.assertRaises(ValueError,msg=kind):self.changed(p)
if __name__=='__main__':unittest.main()
