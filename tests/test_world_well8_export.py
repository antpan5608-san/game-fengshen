"""Current potion event only; controlled export/restore, not App mainline."""
import copy,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex
class Well8ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pin=ex.load(ROOT/'ci/golden-world-well8-content.json');parent=ex.load(ROOT/'ci/golden-world-sages89-content.json')
  cls.apk=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
  base=ci.content(cls.apk,cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(base,parent['iteration']['provenance'],parent)
  cls.out=ex.export_from_base(base,cls.pin['iteration']['provenance'],cls.pin)
 def test_only_existing_potion_capability_and_five_messages_change(self):
  old=json.loads(self.old['scene.json']);new=json.loads(self.out['scene.json'])
  self.assertEqual(69,len(new['maps']));self.assertEqual(378,len(self.out))
  self.assertEqual(self.pin['manifestSha256'],ex.digest(self.out['manifest.json']))
  for key in ('maps','npcs','exits','shops','inns','clinics','ferries','freeBoat','mapArrivals','sceneStories','serviceBindings'):
   self.assertEqual(old.get(key),new.get(key),key)
  self.assertEqual([i for i in old['items']if i['id']!='rom.special.1'],[i for i in new['items']if i['id']!='rom.special.1'])
  item=next(i for i in new['items']if i['id']=='rom.special.1');self.assertEqual('丹藥',item['name'])
  self.assertEqual(dict(targetSpriteId=0,reusable=False,usedFlagId='rom.inventory.special.1.used',evidence='game-data/provenance/world-well8-resources.json'),item['worldUse'])
  self.assertNotIn('buyPrice',item);self.assertNotIn('herbUse',item);self.assertNotIn('battleBindingUse',item)
  self.assertEqual(old['dialogues'],new['dialogues'][:-5]);self.assertEqual([f'rom.dialogue.18.{i}'for i in range(14,19)],[d['id']for d in new['dialogues'][-5:]])
  for name,data in self.old.items():
   if not name.endswith('.json'):self.assertEqual(data,self.out[name],name)
   elif name not in ('scene.json','manifest.json'):
    a=json.loads(data);b=json.loads(self.out[name]);a.pop('version',None);b.pop('version',None);self.assertEqual(a,b,name)
 def test_original_restore_strictly_reproduces_target_in_empty_directory(self):
  with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
   d=Path(td)/'content';ci.restore(self.apk,d,next_code=84)
   self.assertEqual(self.out,{p.name:p.read_bytes()for p in d.iterdir()})
 def test_invented_rewards_actor_facing_gate_changed_text_and_sources_rejected(self):
  original=ex.load;path=ROOT/'game-data/provenance/world-well8-resources.json'
  for mode in ('reward','heal','facing','actor','price','text','cpu','completion'):
   p=copy.deepcopy(original(path))
   if mode=='reward':p['rules']['moneyReward']=100
   elif mode=='heal':p['rules']['healing']=True
   elif mode=='facing':p['rules']['nativeChecksFacing']=True
   elif mode=='actor':p['itemCapabilityUpdates'][0]['fields']['worldUse']['targetSpriteId']=140
   elif mode=='price':p['itemCapabilityUpdates'][0]['fields']['buyPrice']=1
   elif mode=='text':p['dialogues'][0]['text']='invented plague cure'
   elif mode=='cpu':p['cpu'][0]['caseCount']=471
   else:p['rules']['completionFlag']='rom.map.37.flag.128'
   with patch.object(ex,'load',side_effect=lambda q:p if Path(q)==path else original(q)),self.assertRaises(ValueError):ex.validate_world_well8_resources(ex.iteration_reader())
if __name__=='__main__':unittest.main()
