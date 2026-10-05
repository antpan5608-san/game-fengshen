"""Scoped original arrival/letter export; no normal Android mainline claim."""
import copy,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex
class Master172ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pin=ex.load(ROOT/'ci/golden-world-master172-content.json');cls.parent=ex.load(ROOT/'ci/golden-world-west-boat-lotus-content.json')
  cls.apk=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
  cls.base=ci.content(cls.apk,cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.out=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin);cls.scene=json.loads(cls.out['scene.json'])
 def test_native_selector_letter_and_independent_return_preserve_existing_rules_and_media(self):
  s=self.scene;p=json.loads(self.old['scene.json'])
  self.assertEqual(68,len(s['maps']));self.assertEqual(373,len(self.out));self.assertEqual(self.pin['manifestSha256'],ex.digest(self.out['manifest.json']))
  for key in ('items','ferries','freeBoat','sceneStories','serviceBindings','inns','shops','clinics'):self.assertEqual(p.get(key),s.get(key),key)
  self.assertEqual(p['npcs'],s['npcs'][:-3]);self.assertEqual(p['exits'],s['exits'][:-1])
  self.assertEqual(171,s['mapArrivals'][0]['requestedMapId']);self.assertEqual(172,s['mapArrivals'][0]['actualMapId'])
  self.assertEqual(['rom.dialogue.182.0','rom.dialogue.182.1','rom.dialogue.182.2'],[n['firstDialogue']for n in s['npcs'][-3:]])
  self.assertTrue(all(n['readOnlyDialogue']and not n['firstEffects']for n in s['npcs'][-3:]))
  self.assertEqual([32,12],s['exits'][-1]['spawn']);self.assertEqual([7,14],s['exits'][-1]['trigger'])
  for name,data in self.old.items():
   if not name.endswith('.json'):self.assertEqual(data,self.out[name],name)
   elif name not in ('scene.json','manifest.json'):
    before=json.loads(data);after=json.loads(self.out[name]);before.pop('version',None);after.pop('version',None);self.assertEqual(before,after,name)
  room=json.loads(self.out['scene172.json']);self.assertEqual([0,2],room['walkableClasses']);self.assertEqual({0,1,2},set(room['collision']))
 def test_original_restore_reproduces_target_from_empty_directory(self):
  with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
   d=Path(td)/'content';ci.restore(self.apk,d,next_code=84)
   self.assertEqual(self.out,{p.name:p.read_bytes()for p in d.iterdir()})
 def test_fake_gift_plague_gate_altered_font_and_swapped_return_are_rejected(self):
  original=ex.load;path=ROOT/'game-data/provenance/world-master172-resources.json'
  for mode in ('gift','gate','font','return','npc','matrix'):
   proof=copy.deepcopy(original(path))
   if mode=='gift':proof['npcs'][2]['firstEffects']=[dict(kind='money',amount=100)]
   elif mode=='gate':proof['arrival']['itemId']='rom.map.37.flag.128'
   elif mode=='font':proof['dialogues'][2]['text']='invented cure reward'
   elif mode=='return':proof['exits'][0]['spawn']=[7,14]
   elif mode=='npc':proof['npcs'][2]['firstDialogue']='rom.dialogue.182.5'
   else:proof['cpuExpected']['caseCount']=255
   with patch.object(ex,'load',side_effect=lambda p:proof if Path(p)==path else original(p)),self.assertRaises(ValueError):ex.validate_world_master172_resources(ex.iteration_reader())
if __name__=='__main__':unittest.main()
