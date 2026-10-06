"""Regression for the actual c59 loader failure, not a normal App route."""
import copy,json,sys,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex

class ReferenceRepairTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pin=ex.load(ROOT/'ci/golden-world-reference-repair-content.json')
  parent=ex.load(ROOT/'ci/golden-world-well8-content.json')
  apk=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
  base=ci.content(apk,cls.pin['iteration']['base'])
  cls.old=ex.export_from_base(base,parent['iteration']['provenance'],parent)
  cls.new=ex.export_from_base(base,cls.pin['iteration']['provenance'],cls.pin)
 def test_reported_missing_bindings_and_untalkable_bed_are_repaired_without_rule_changes(self):
  old=json.loads(self.old['scene.json']);new=json.loads(self.new['scene.json'])
  oldids={d['id']for d in old['dialogues']};ds={d['id']:d for d in new['dialogues']}
  self.assertNotIn('rom.dialogue.148.3',oldids)
  self.assertEqual(self.pin['manifestSha256'],ex.digest(self.new['manifest.json']))
  for n in new['npcs']:
   self.assertTrue(n.get('talkDisabled')or n.get('treasure')or n.get('moneyTreasure')or n['firstDialogue']in ds,n['id'])
   self.assertTrue(not n.get('repeatDialogue')or n['repeatDialogue']in ds,n['id'])
   for message in n.get('originalTalk',{}).get('messageDialogues',{}).values():self.assertIn(message,ds,n['id'])
  for i in range(3,9):
   alias=ds[f'rom.dialogue.148.{i}'];source=ds[f'rom.dialogue.158.{i}']
   self.assertEqual(source['text'],alias['text']);self.assertEqual(source['source']['record'],alias['source']['record'])
   self.assertEqual(source['id'],alias['source']['bindingAliasOf']);self.assertEqual(158,alias['source']['originalTextGroup'])
  for key in ('initialPlayer','additionalCharacters','items','exits','maps','shops','inns','clinics','sceneStories','ferries','freeBoat','mapArrivals','serviceBindings'):
   self.assertEqual(old.get(key),new.get(key),key)
  oldn={n['id']:n for n in old['npcs']}
  for n in new['npcs']:
   value=copy.deepcopy(n)
   if n['id']=='rom.npc.37.yang-bed':
    self.assertTrue(value.pop('talkDisabled'));value.pop('untalkableEvidence')
   self.assertEqual(oldn[n['id']],value,n['id'])
  for name,raw in self.old.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.new[name],name)
   elif name not in ('scene.json','manifest.json'):
    a=json.loads(raw);b=json.loads(self.new[name]);a.pop('version',None);b.pop('version',None);self.assertEqual(a,b,name)
 def test_wrong_original_group_foreign_alias_or_bed_cannot_bypass_reference_checks(self):
  path=ROOT/'game-data/provenance/world-jiameng-reference-repair.json';load=ex.load
  evidence=load(ROOT/self.pin['iteration']['provenance'])
  for field in ('group','binding','source','record','bed'):
   proof=copy.deepcopy(load(path))
   if field=='group':proof['dialogueAliases'][0]['originalGroup']=148
   elif field=='binding':proof['dialogueAliases'][0]['bindingId']='rom.dialogue.148.99'
   elif field=='source':proof['dialogueAliases'][0]['sourceId']='rom.dialogue.158.4'
   elif field=='record':proof['dialogueAliases'][0]['sourceRecord']['sha256']='0'*64
   else:proof['untalkableBed']['id']='rom.npc.37.0'
   with patch.object(ex,'load',side_effect=lambda p:proof if Path(p)==path else load(p)),self.assertRaises(ValueError):
    ex.export_world_from_base(self.old,evidence,self.pin['iteration']['provenance'],self.pin)

if __name__=='__main__':unittest.main()
