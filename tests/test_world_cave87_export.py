"""Scoped actual-ROM restoration; CPU fixtures and output hashes are not App acceptance."""
import json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
class Cave87ExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
  # Freeze this recipe independently of subsequent tasks/content updates.
  if cls.pin['iteration']['provenance']!='game-data/provenance/world-cave87-content.json':
   cls.pin=json.loads((ci.ROOT/'ci/golden-world-cave87-content.json').read_text(encoding='utf-8'))
  cls.proof=cls.pin['iteration']['provenance']
  cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
  cls.parent=json.loads((ci.ROOT/'ci/golden-world-village5-hidden-content.json').read_text(encoding='utf-8'))
  cls.before=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
  cls.result=ex.export_from_base(cls.base,cls.proof,cls.pin)
 def test_complete_manifest_repeatable_parent_media_and_independent_real_exits(self):
  self.assertEqual(self.result,ex.export_from_base(self.base,self.proof,self.pin))
  for name,raw in self.before.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
  for name,h in json.loads(self.result['manifest.json'])['files'].items():self.assertEqual(h,ci.sha(self.result[name]))
  world=json.loads(self.result['scene.json']);maps=json.loads(self.result['scene87.json'])
  self.assertEqual((32,15,[19,13]),(maps['width'],maps['height'],maps['spawn']))
  self.assertEqual({0,1},set(maps['collision']))
  exits={(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in world['exits']}
  self.assertIn((5,(7,6),87,(19,13)),exits);self.assertIn((87,(19,13),5,(7,6)),exits)
 def test_actual_boss_scripts_seven_dialogues_and_chest_category_not_guessed(self):
  combat=json.loads(self.result['combat.json']);world=json.loads(self.result['scene.json'])
  boss=next(b for b in combat['bosses']if b['id']=='rom.boss.156')
  self.assertEqual((87,1,7,6,170,156),(boss['mapId'],boss['entryTrigger']['x'],boss['entryTrigger']['y'],boss['eventId'],boss['sourceType'],boss['enemyId']))
  self.assertEqual([f'rom.dialogue.97.{i}'for i in range(11,18)],boss['continuation']['dialogueIds'])
  self.assertEqual('xiaolongnv',boss['continuation']['departureCharacterId'])
  self.assertEqual([3,5],[m['index']for m in boss['continuation']['movementsBeforeDialogue']])
  ns={n['id']:n for n in world['npcs']if n['mapId']==87};self.assertEqual(8,len(ns))
  self.assertEqual('rom.medicine.0',ns['rom.npc.87.6']['treasure']['itemId'])
  self.assertEqual((550,'rom.map.87.flag.8'),(ns['rom.npc.87.4']['moneyTreasure']['amount'],ns['rom.npc.87.4']['moneyTreasure']['flagId']))
  enemy=next(e for e in combat['enemies']if e['id']==156)
  self.assertEqual((4500,294,150,2000,1600,9),(enemy['hp'],enemy['attack'],enemy['defense'],enemy['experienceReward'],enemy['moneyReward'],enemy['behaviorByte']))
  zone=next(z for z in combat['zones']if z['mapId']==87);self.assertEqual((24,10),(zone['id'],len(zone['groups'])))
 def test_wrong_departure_trigger_script_or_missing_original_cpu_rejected(self):
  path='game-data/provenance/world-cave87-state.json';original=json.loads((ci.ROOT/path).read_text(encoding='utf-8'))
  for kind in ('map','departure','reward','order','motion','pixel','cpu'):
   proof=json.loads(json.dumps(original))
   if kind=='map':proof['boss']['entryTrigger']['mapId']=135
   elif kind=='departure':proof['boss']['continuation']['departureCharacterId']='yangjian'
   elif kind=='reward':proof['rules']['extraEventReward']=True
   elif kind=='order':proof['boss']['continuation']['dialogueIds'].reverse()
   elif kind=='motion':proof['boss']['approach']['completedSteps']=5
   elif kind=='pixel':proof['graphics']['156']['rgbaSha256']='0'*64
   else:proof['cpu'].pop()
   def load(p):return proof if Path(p).resolve()==(ci.ROOT/path).resolve()else json.loads(Path(p).read_text(encoding='utf-8'))
   with patch.object(ex,'load',side_effect=load):
    with self.assertRaises(ValueError,msg=kind):ex.export_from_base(self.base,self.proof,self.pin)
if __name__=='__main__':unittest.main()
