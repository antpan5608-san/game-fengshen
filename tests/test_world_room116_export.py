"""Optional original jail through the existing exporter, source and topology gates."""
import collections,copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci,export_development as ex

class Room116ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path='game-data/provenance/world-room116-content.json';cls.proofPath='game-data/provenance/world-room116-state.json'
  cls.p=ex.load(ci.ROOT/cls.path);cls.parent=ex.load(ci.ROOT/cls.p['baseExport']['pinPath']);cls.pin=copy.deepcopy(cls.parent)
  cls.pin['contentVersion']='opening-segment-001-c50';cls.pin['iteration']['provenance']=cls.path
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.out=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False);cls.pin['manifestSha256']=ci.sha(cls.out['manifest.json']);cls.scene=json.loads(cls.out['scene.json'])
 def test_strict_repeatable_manifest_and_old_media_byte_exact(self):
  self.assertEqual(self.out,ex.export_from_base(self.base,self.path,self.pin));ci.validate_item_sources(self.out)
  self.assertEqual((302,56),(len(self.out),len(self.scene['maps'])))
  for n,raw in self.old.items():
   if not n.endswith('.json'):self.assertEqual(raw,self.out[n],n)
  for n,sha in json.loads(self.out['manifest.json'])['files'].items():self.assertEqual(sha,ci.sha(self.out[n]))
  npcs=[n for n in self.scene['npcs']if n['mapId']==116];self.assertEqual(6,len(npcs))
  self.assertTrue(all(n['firstEffects']==[]for n in npcs))
  self.assertEqual(41,npcs[0]['originalTalk']['actionId'])
  self.assertTrue(all(n['removedFlagId']=='rom.npccontext.116.210'for n in npcs))
 def test_actual_optional_route_repeated_text_and_no_invented_gate_or_wall(self):
  def reach(mid,start,end):
   m=json.loads(self.out[f'scene{mid}.json']);w=m['width'];q=collections.deque([start]);seen={start};exits={tuple(e['trigger'])for e in self.scene['exits']if e['fromMapId']==mid and e.get('triggerMode')!='EDGE'}
   while q:
    x,y=q.popleft()
    if(x,y)==end:return True
    for nx,ny in [(x+1,y),(x-1,y),(x,y+1),(x,y-1)]:
     p=(nx,ny);i=ny*w+nx
     if not(0<=nx<w and 0<=ny<m['height'])or p in seen or i not in m['enabledCells']or i in m['dynamicObjectCells']or(p in exits and p!=end):continue
     seen.add(p);q.append(p)
   return False
  for mid,start,end in [(141,(6,19),(3,7)),(116,(5,4),(6,4))]:self.assertTrue(reach(mid,start,end),(mid,start,end))
  # Original static topology does NOT prove the optional component reachable
  # from the verified middle entry. Keep this exact debt; never open a wall.
  self.assertFalse(reach(115,(32,28),(7,44)));self.assertFalse(reach(141,(15,29),(3,7)))
  self.assertFalse(reach(116,(5,2),(5,4))) # Real actor is directly below entry; talk, not walk through.
  links=[e for e in self.scene['exits']if e['fromMapId']==116 or e['toMapId']==116]
  self.assertEqual([(141,[3,7],116,[5,2]),(116,[5,2],141,[3,7])],[(e['fromMapId'],e['trigger'],e['toMapId'],e['spawn'])for e in links])
  m=json.loads(self.out['scene116.json']);self.assertTrue(all(i not in m['enabledCells']for i,c in enumerate(m['collision'])if c==1))
  self.assertNotIn('disguiseRequired',m);self.assertFalse(any(e.get('requiredFlag')=='rom.map.116.flag.128'for e in self.scene['exits']))
  self.assertEqual([6,3],next(n['stateVariant']['cell']for n in self.scene['npcs']if n['id']=='rom.npc.116.0'))
 def test_wrong_actor_gift_timer_flag_or_wall_rejected(self):
  original=ex.load(ci.ROOT/self.proofPath)
  for case in ['event','before','gift','timer','wall','pixels']:
   p=copy.deepcopy(self.p);proof=copy.deepcopy(original)
   if case=='event':proof['rules']['eventId']=41
   elif case=='before':proof['rules']['flagBeforeText']=False
   elif case=='gift':p['npcs'][0]['firstEffects']=[dict(type='money',amount=500)]
   elif case=='timer':proof['rules']['costumeTimer']=100
   elif case=='wall':p['maps'][0]['walkableClasses'].append(1)
   else:p['graphics'][p['npcs'][0]['sprite']]['tiles'][0]['flipX']=True
   def load(path):
    path=Path(path).resolve()
    if path==(ci.ROOT/self.path).resolve():return p
    if path==(ci.ROOT/self.proofPath).resolve():return proof
    return json.loads(path.read_text(encoding='utf-8'))
   with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
