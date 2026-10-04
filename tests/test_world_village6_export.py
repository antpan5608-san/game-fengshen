"""Target village caller data, actual text/conditions and preserved reviewed media."""
import collections,copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex

class Village6ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path='game-data/provenance/world-village6-content.json';cls.proofPath='game-data/provenance/world-village-batch-resources.json'
  cls.p=ex.load(ci.ROOT/cls.path);cls.parent=ex.load(ci.ROOT/cls.p['baseExport']['pinPath']);cls.pin=copy.deepcopy(cls.parent)
  cls.pin['contentVersion']='opening-segment-001-c47';cls.pin['iteration']['provenance']=cls.path
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.out=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False);cls.pin['manifestSha256']=ci.sha(cls.out['manifest.json'])
  cls.scene=json.loads(cls.out['scene.json'])
 def test_repeatable_manifest_original_media_and_independent_edge_return(self):
  self.assertEqual(self.out,ex.export_from_base(self.base,self.path,self.pin));ci.validate_item_sources(self.out)
  self.assertEqual((265,49),(len(self.out),len(self.scene['maps'])))
  for name,raw in self.old.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.out[name],name)
  for name,h in json.loads(self.out['manifest.json'])['files'].items():self.assertEqual(h,ci.sha(self.out[name]))
  exits=[e for e in self.scene['exits']if e['fromMapId']==6 and e['toMapId']==16]
  self.assertEqual(1,len(exits));self.assertEqual(('EDGE','DOWN',[15,29],[63,135]),(exits[0]['triggerMode'],exits[0]['direction'],exits[0]['trigger'],exits[0]['spawn']))
  self.assertEqual([[63,135],[64,135]],[e['trigger']for e in self.scene['exits']if e['fromMapId']==16 and e['toMapId']==6])
 def test_actual_stocks_prices_font_conditional_speech_and_invisible_pickup(self):
  for room,ids in [(17,[9,24,36]),(18,[5,13,19,31,39]),(19,[1,2,4,6,7,10,12])]:
   shop=next(s for s in self.scene['shops']if s['id']==f'rom.shop.6.{room}')
   self.assertEqual(ids,[int(i.split('.')[-1])for i in shop['items']]);self.assertEqual(shop['items'],shop['sellItems'])
  self.assertEqual(200,next(i for i in self.scene['inns']if i['id']=='rom.inn.6')['price'])
  self.assertEqual(6,len([b for b in self.scene['serviceBindings']if b['callerMapId']==6]))
  v,_=ex.validate_world_village_batch_resources(ex.iteration_reader(),6)
  self.assertEqual([6144,9],[t['caseCount']for t in v['cpu']])
  texts={d['id']:d['text']for d in v['dialogues']}
  self.assertEqual('『女兒村裏的男人都被捉走了。』',texts['rom.dialogue.16.2'])
  self.assertIn('捆妖繩',texts['rom.dialogue.16.9']);self.assertTrue(texts['rom.dialogue.16.10'].startswith('『謝謝'))
  ns=[n for n in self.scene['npcs']if n['mapId']==6];self.assertEqual(8,len(ns))
  for n in ns[:3]:self.assertEqual('rom.global.7c6.64',n['originalTalk']['witnessFlagId']);self.assertEqual([],n['firstEffects'])
  n=ns[3];self.assertTrue(n['hiddenInvestigation']);self.assertEqual('rom.medicine.1',n['treasure']['itemId']);self.assertEqual('rom.map.6.flag.8',n['treasure']['flagId']);self.assertNotIn('removedFlagId',n)
  from PIL import Image
  import io
  self.assertFalse(any(Image.open(io.BytesIO(self.out[n['sprite']])).getchannel('A').getdata()))
  m=json.loads(self.out['scene6.json']);w=m['width'];seen={(15,28)};q=collections.deque(seen)
  while q:
   x,y=q.popleft();sc=m['collision'][y*w+x]
   for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
    nx,ny=x+dx,y+dy
    if not(0<=nx<w and 0<=ny<m['height']):continue
    i=ny*w+nx
    if i not in m['enabledCells']or i in m['dynamicObjectCells']or key in m['sourceEdges'].get(str(sc),[])or key in m['targetEdges'].get(str(m['collision'][i]),[]):continue
    if(nx,ny)not in seen:seen.add((nx,ny));q.append((nx,ny))
  for e in self.p['exits']:
   if e['fromMapId']==6 and e.get('captureCaller'):self.assertIn(tuple(e['trigger']),seen,e)
  self.assertTrue(any(c==1 for c in m['collision']))
  self.assertTrue(all(i not in m['enabledCells'] for i,c in enumerate(m['collision']) if c==1))
 def test_actual_world_route_and_every_resident_remain_reachable_without_teleport(self):
  m=json.loads(self.out['scene16.json']);w=m['width'];exits={tuple(e['trigger']) for e in self.scene['exits'] if e['fromMapId']==16}
  start=(107,157);target=(63,135);q=collections.deque([start]);seen={start}
  while q:
   x,y=q.popleft()
   if (x,y)==target:break
   for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
    nx,ny=x+dx,y+dy
    if not(0<=nx<w and 0<=ny<m['height']):continue
    i=ny*w+nx;p=(nx,ny)
    if i not in m['enabledCells']or i in m['dynamicObjectCells']or p in seen or(p in exits and p!=target)or key in m['sourceEdges'].get(str(m['collision'][y*w+x]),[])or key in m['targetEdges'].get(str(m['collision'][i]),[]):continue
    seen.add(p);q.append(p)
  self.assertIn(target,seen)
  m=json.loads(self.out['scene6.json']);w=m['width'];exits={tuple(e['trigger'])for e in self.scene['exits']if e['fromMapId']==6 and e.get('triggerMode')!='EDGE'}
  seen={(15,28)};q=collections.deque(seen)
  while q:
   x,y=q.popleft()
   for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
    nx,ny=x+dx,y+dy
    if not(0<=nx<w and 0<=ny<m['height']):continue
    i=ny*w+nx;p=(nx,ny)
    if i not in m['enabledCells']or i in m['dynamicObjectCells']or p in seen or p in exits or key in m['sourceEdges'].get(str(m['collision'][y*w+x]),[])or key in m['targetEdges'].get(str(m['collision'][i]),[]):continue
    seen.add(p);q.append(p)
  for npc in [n for n in self.scene['npcs'] if n['mapId']==6]:
   x,y=npc['cell'];self.assertTrue(any(p in seen for p in[(x,y-1),(x,y+1),(x-1,y),(x+1,y)]),npc['id'])
  self.assertIn((17,5),seen)
  self.assertNotIn((18,6),seen)

 def test_wrong_gate_inventory_pose_text_price_or_geometry_rejected(self):
  original=ex.load(ci.ROOT/self.proofPath)
  for kind in ['witness','grant','glyph','palette','price','wall','pose']:
   p=copy.deepcopy(self.p);proof=copy.deepcopy(original)
   if kind=='witness':p['npcs'][0]['originalTalk']['witnessFlagId']='rom.global.7c6.16'
   elif kind=='grant':p['npcs'][3]['treasure']['amount']=2
   elif kind=='glyph':proof['villages'][0]['font']['charset']['25']='洞'
   elif kind=='palette':p['maps'][0]['palette']=[0]*32
   elif kind=='price':p['inns'][0]['price']=1
   elif kind=='wall':p['maps'][0]['walkableClasses'].append(1)
   else:proof['villages'][0]['graphics'][proof['villages'][0]['npcs'][0]['sprite']]['frameRecordSource']=proof['villages'][0]['npcs'][1]['source']['record']
   def load(path):
    resolved=Path(path).resolve()
    if resolved==(ci.ROOT/self.path).resolve():return p
    if resolved==(ci.ROOT/self.proofPath).resolve():return proof
    return json.loads(Path(path).read_text(encoding='utf-8'))
   with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=kind):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
