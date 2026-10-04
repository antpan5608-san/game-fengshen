"""C6 hidden discovery remains a real blocked actor, without fake visible art."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
class HiddenVillage5Test(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.path='game-data/provenance/world-village5-hidden-content.json';cls.p=ex.load(ci.ROOT/cls.path);cls.parent=ex.load(ci.ROOT/cls.p['baseExport']['pinPath']);cls.pin=copy.deepcopy(cls.parent);cls.pin['contentVersion']='opening-segment-001-c45';cls.pin['iteration']['provenance']=cls.path
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base']);cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.out=ex.export_from_base(cls.base,cls.path,cls.pin,verify_target=False);cls.pin['manifestSha256']=ci.sha(cls.out['manifest.json']);cls.scene=json.loads(cls.out['scene.json'])
 def test_exact_repeatable_original_material_and_hidden_actor(self):
  self.assertEqual(self.out,ex.export_from_base(self.base,self.path,self.pin));ci.validate_item_sources(self.out)
  self.assertEqual((249,47),(len(self.out),len(self.scene['maps'])))
  for name,raw in self.old.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.out[name],name)
  n=next(n for n in self.scene['npcs']if n['id']=='rom.npc.5.5');self.assertEqual('rom.medicine.1',n['treasure']['itemId']);self.assertEqual('',n['firstDialogue']);self.assertNotIn('removedFlagId',n)
  self.assertEqual('参须',next(i['name']for i in self.scene['items']if i['id']=='rom.medicine.1'))
  m=json.loads(self.out['scene5.json']);old=json.loads(self.old['scene5.json']);self.assertEqual(old['collision'],m['collision']);self.assertEqual(old['enabledCells'],m['enabledCells']);self.assertEqual(sorted(old['dynamicObjectCells']+[7*32+15]),m['dynamicObjectCells'])
  from PIL import Image
  import io
  self.assertFalse(any(Image.open(io.BytesIO(self.out[n['sprite']])).getchannel('A').getdata()))
 def test_wrong_id_quantity_flag_visible_pose_or_removal_rejected(self):
  for field in ['item','quantity','flag','hidden','removed','sprite']:
   p=copy.deepcopy(self.p);n=p['npcs'][0]
   if field=='item':n['treasure']['itemId']='rom.medicine.0'
   elif field=='quantity':n['treasure']['amount']=2
   elif field=='flag':n['treasure']['flagId']='rom.map.5.flag.2'
   elif field=='hidden':n['hiddenInvestigation']=False
   elif field=='removed':n['removedFlagId']='rom.map.5.flag.1'
   else:n['sprite']='fake-icon.png'
   def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
   with self.assertRaises(ValueError,msg=field),patch.object(ex,'load',side_effect=load):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
