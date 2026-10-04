"""Actual local target maps, lighting and sources through the original exporter."""
import collections,copy,io,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex

class Night8ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path='game-data/provenance/world-night8-content.json';cls.proofPath='game-data/provenance/world-night8-resources.json'
  cls.p=ex.load(ci.ROOT/cls.path);cls.parent=ex.load(ci.ROOT/cls.p['baseExport']['pinPath']);cls.pin=copy.deepcopy(cls.parent)
  cls.pin['contentVersion']='opening-segment-001-c48';cls.pin['iteration']['provenance']=cls.path
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.out=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False);cls.pin['manifestSha256']=ci.sha(cls.out['manifest.json']);cls.scene=json.loads(cls.out['scene.json'])
 def test_strict_repeatable_manifest_unchanged_media_and_real_palette_variant(self):
  self.assertEqual(self.out,ex.export_from_base(self.base,self.path,self.pin));ci.validate_item_sources(self.out)
  self.assertEqual((276,52),(len(self.out),len(self.scene['maps'])))
  for name,raw in self.old.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.out[name],name)
  for name,sha in json.loads(self.out['manifest.json'])['files'].items():self.assertEqual(sha,ci.sha(self.out[name]))
  dark=Image.open(io.BytesIO(self.out['tiles74.png'])).convert('RGBA');light=Image.open(io.BytesIO(self.out['tiles74-lit.png'])).convert('RGBA')
  self.assertNotEqual(dark.tobytes(),light.tobytes());self.assertEqual((256,256),light.size)
  self.assertGreater(len(set(light.getdata())),len(set(dark.getdata())))
  self.assertEqual('runtime.map74.light.active',self.scene['nightLightAtlas']['activeFlag'])
 def test_original_routes_to_teacher_and_usable_rope_chest_without_added_item_gate(self):
  def reach(mid,start,goal):
   m=json.loads(self.out[f'scene{mid}.json']);w=m['width'];q=collections.deque([start]);seen={start};exits={tuple(e['trigger'])for e in self.scene['exits']if e['fromMapId']==mid}
   while q:
    x,y=q.popleft()
    if(x,y)==goal:return True
    for key,dx,dy in [('UP',0,-1),('DOWN',0,1),('LEFT',-1,0),('RIGHT',1,0)]:
     nx,ny=x+dx,y+dy;p=(nx,ny)
     if not(0<=nx<w and 0<=ny<m['height']):continue
     i=ny*w+nx
     if p in seen or i not in m['enabledCells']or i in m['dynamicObjectCells']or(p in exits and p!=goal)or key in m.get('sourceEdges',{}).get(str(m['collision'][y*w+x]),[])or key in m.get('targetEdges',{}).get(str(m['collision'][i]),[]):continue
     seen.add(p);q.append(p)
   return False
  self.assertTrue(reach(16,(63,135),(78,158)));self.assertTrue(reach(16,(63,135),(70,105)))
  self.assertTrue(reach(100,(8,51),(20,11)));self.assertTrue(reach(164,(7,14),(7,5)))
  n=next(n for n in self.scene['npcs']if n['id']=='rom.npc.74.5');self.assertEqual('rom.special.13',n['treasure']['itemId'])
  x,y=n['cell'];self.assertTrue(any(reach(74,(3,27),p)for p in[(x,y-1),(x,y+1),(x-1,y),(x+1,y)]))
  self.assertEqual(120,next(n['moneyTreasure']['amount']for n in self.scene['npcs']if n['id']=='rom.npc.74.4'))
  for mid in [74,100,164]:
   m=json.loads(self.out[f'scene{mid}.json']);self.assertTrue(all(i not in m['enabledCells']for i,c in enumerate(m['collision'])if c==1));self.assertNotIn('night8OwnershipRequired',m)
  self.assertEqual([52,53,56,57],[e['id']for e in self.p['combatOverlay']['enemies']])
  self.assertEqual([25,26],[z['id']for z in self.p['combatOverlay']['zones']]);self.assertEqual(7,next(e['behaviorByte']for e in self.p['combatOverlay']['enemies']if e['id']==57))
 def test_wrong_light_gift_price_or_original_wall_rejected(self):
  original=ex.load(ci.ROOT/self.proofPath)
  for kind in ['gift','light','reusable','glyph','wall','price']:
   p=copy.deepcopy(self.p);proof=copy.deepcopy(original)
   if kind=='gift':p['npcs'][1]['originalTalk']['itemId']='rom.special.9'
   elif kind=='light':proof['lighting']['paletteSelector']=52
   elif kind=='reusable':proof['items'][0]['nightLightUse']['reusable']=False
   elif kind=='glyph':proof['font']['charset']['18']='火'
   elif kind=='wall':p['maps'][0]['walkableClasses'].append(1)
   else:p['items'][0]['buyPrice']=1
   def load(path):
    path=Path(path).resolve()
    if path==(ci.ROOT/self.path).resolve():return p
    if path==(ci.ROOT/self.proofPath).resolve():return proof
    return json.loads(path.read_text(encoding='utf-8'))
   with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=kind):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
