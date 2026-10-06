"""Scoped original firecloud/potion export; no normal Android mainline claim."""
import copy,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex
class Sages89ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pin=ex.load(ROOT/'ci/golden-world-sages89-content.json');cls.parent=ex.load(ROOT/'ci/golden-world-master172-content.json')
  cls.apk=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
  cls.base=ci.content(cls.apk,cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.out=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin);cls.scene=json.loads(cls.out['scene.json'])
 def test_native_firecloud_gift_independent_exits_and_existing_state_and_media(self):
  s=self.scene;p=json.loads(self.old['scene.json'])
  self.assertEqual(69,len(s['maps']));self.assertEqual(378,len(self.out));self.assertEqual(self.pin['manifestSha256'],ex.digest(self.out['manifest.json']))
  for key in ('ferries','freeBoat','sceneStories','serviceBindings','inns','shops','clinics','mapArrivals'):self.assertEqual(p.get(key),s.get(key),key)
  self.assertEqual(p['items'],s['items'][:-1]);self.assertEqual(p['npcs'],s['npcs'][:-3]);self.assertEqual(p['exits'],s['exits'][:-2])
  item=s['items'][-1];self.assertEqual('rom.special.1',item['id']);self.assertEqual('丹藥',item['name']);self.assertNotIn('worldUse',item)
  npc=s['npcs'][-3];self.assertEqual(1,npc['originalTalk']['actionId']);self.assertEqual('rom.map.89.flag.1',npc['originalTalk']['mapFlagId'])
  self.assertTrue(all(n['readOnlyDialogue']and not n['firstEffects']for n in s['npcs'][-2:]))
  self.assertEqual([[219,144],[8,13]],[e['trigger']for e in s['exits'][-2:]])
  self.assertEqual([[8,13],[219,144]],[e['spawn']for e in s['exits'][-2:]])
  for name,data in self.old.items():
   if not name.endswith('.json'):self.assertEqual(data,self.out[name],name)
   elif name not in ('scene.json','manifest.json'):
    before=json.loads(data);after=json.loads(self.out[name]);before.pop('version',None);after.pop('version',None)
    if name=='scene16.json':
     # Only the two independently verified firecloud rows add this original transition cell.
     added={e[field][1]*before['width']+e[field][0]for e in s['exits'][-2:]for field,mid in [('trigger','fromMapId'),('spawn','toMapId')]if e[mid]==16}
     self.assertEqual({144*before['width']+219},added)
     for key in ('transitionCells','enabledCells'):before[key]=sorted(set(before[key])|added)
    self.assertEqual(before,after,name)
  room=json.loads(self.out['scene89.json']);self.assertEqual([0,4,8],room['walkableClasses']);self.assertEqual({0,1,4,8},set(room['collision']));self.assertEqual(3,room['terrain']['tileset'])
 def test_original_restore_reproduces_target_from_empty_directory(self):
  with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
   d=Path(td)/'content';ci.restore(self.apk,d,next_code=84)
   self.assertEqual(self.out,{p.name:p.read_bytes()for p in d.iterdir()})
 def test_guessed_heal_extra_rewards_plague_lock_and_wrong_return_rejected(self):
  original=ex.load;path=ROOT/'game-data/provenance/world-sages89-resources.json'
  for mode in ('heal','gift','gate','font','return','full','matrix'):
   proof=copy.deepcopy(original(path))
   if mode=='heal':proof['items'][0]['herbUse']=dict(healHp=9999)
   elif mode=='gift':proof['npcs'][1]['firstEffects']=[dict(kind='money',amount=100)]
   elif mode=='gate':proof['rules']['noEntryPrerequisite']=False
   elif mode=='font':proof['dialogues'][2]['text']='invented cure reward'
   elif mode=='return':proof['exits'][1]['spawn']=[8,13]
   elif mode=='full':proof['rules']['fullInventoryKeepsFlag']=False
   else:proof['cpu'][2]['caseCount']=64
   with patch.object(ex,'load',side_effect=lambda p:proof if Path(p)==path else original(p)),self.assertRaises(ValueError):ex.validate_world_sages89_resources(ex.iteration_reader())
if __name__=='__main__':unittest.main()
