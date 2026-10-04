"""Scoped Queen target: original exporter, full route topology and strict parents."""
import copy,json,os,sys,unittest,collections
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci,export_development as ex

class QueenExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path='game-data/provenance/world-queen117-content.json';cls.proofPath='game-data/provenance/world-queen117-state.json'
  cls.p=ex.load(ci.ROOT/cls.path);cls.parent=ex.load(ci.ROOT/cls.p['baseExport']['pinPath']);cls.pin=copy.deepcopy(cls.parent)
  cls.pin['contentVersion']='opening-segment-001-c49';cls.pin['iteration']['provenance']=cls.path
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.out=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False);cls.pin['manifestSha256']=ci.sha(cls.out['manifest.json']);cls.scene=json.loads(cls.out['scene.json'])
 def test_clean_repeatable_files_real_rules_and_byte_identical_old_media(self):
  self.assertEqual(self.out,ex.export_from_base(self.base,self.path,self.pin));ci.validate_item_sources(self.out)
  self.assertEqual((294,55),(len(self.out),len(self.scene['maps'])))
  for name,raw in self.old.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.out[name],name)
  for name,sha in json.loads(self.out['manifest.json'])['files'].items():self.assertEqual(sha,ci.sha(self.out[name]))
  items={v['id']:v for v in self.scene['items']};self.assertEqual('捆妖繩',items['rom.special.13']['name'])
  self.assertEqual(2,items['rom.special.13']['battleBindingUse']['bindingMarker'])
  self.assertNotIn('battleBindingUse',items['rom.special.18']);self.assertEqual('攢心釘',items['rom.special.18']['name'])
  boss=next(v for v in json.loads(self.out['combat.json'])['bosses']if v['id']=='rom.boss.157')
  self.assertEqual(0,boss['intro']['completedSteps']);self.assertTrue(boss['commitAfterDialogue'])
 def test_real_main_route_return_and_no_original_wall_or_optional_lock_invented(self):
  def reachable(mid,start,goal):
   m=json.loads(self.out[f'scene{mid}.json']);w=m['width'];q=collections.deque([start]);seen={start};exits={tuple(e['trigger'])for e in self.scene['exits']if e['fromMapId']==mid and e.get('triggerMode')!='EDGE'}
   while q:
    x,y=q.popleft()
    if(x,y)==goal:return True
    for nx,ny in [(x+1,y),(x-1,y),(x,y+1),(x,y-1)]:
     if not(0<=nx<w and 0<=ny<m['height']):continue
     p=(nx,ny);i=ny*w+nx
     if p in seen or i not in m['enabledCells']or i in m['dynamicObjectCells']or(p in exits and p!=goal):continue
     seen.add(p);q.append(p)
   return False
  for mid,start,end in [(141,(15,29),(15,15)),(115,(32,28),(56,44)),(141,(24,19),(26,7)),(117,(7,13),(7,5))]:
   self.assertTrue(reachable(mid,start,end),(mid,start,end))
  edge=next(e for e in self.scene['exits']if e['fromMapId']==141 and e.get('triggerMode')=='EDGE')
  self.assertEqual(([15,29],16,[91,134],'DOWN'),(edge['trigger'],edge['toMapId'],edge['spawn'],edge['direction']))
  self.assertFalse(any(e['fromMapId']==115 and e['trigger']==[6,14]for e in self.scene['exits']))
  self.assertEqual([141], [e['toMapId']for e in self.scene['exits']if e['fromMapId']==115 and e['trigger']==[56,44]])
  for mid in [141,115,117]:
   m=json.loads(self.out[f'scene{mid}.json']);self.assertTrue(all(i not in m['enabledCells']for i,c in enumerate(m['collision'])if c==1))
  self.assertFalse(any(m['id']==116 for m in self.scene['maps']))
  self.assertTrue(any(e['toMapId']==116 for e in self.p['inactiveExitRecords']))
 def test_wrong_binding_parent_wall_and_huang_reward_rejected(self):
  original=ex.load(ci.ROOT/self.proofPath)
  for case in ['marker','parent','wall','gift']:
   p=copy.deepcopy(self.p);proof=copy.deepcopy(original)
   if case=='marker':p['combatOverlay']['enemies'][-1]['requiredBindingMarker']=1
   elif case=='parent':p['existingItemCapabilityUpdates'][0]['baseDefinitionSha256']='0'*64
   elif case=='wall':p['maps'][0]['walkableClasses'].append(1)
   else:proof['huang']['item']['originalId']=19
   def load(path):
    path=Path(path).resolve()
    if path==(ci.ROOT/self.path).resolve():return p
    if path==(ci.ROOT/self.proofPath).resolve():return proof
    return json.loads(path.read_text(encoding='utf-8'))
   with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
