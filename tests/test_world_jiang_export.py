"""Bounded c61 export/actual exit derivation; no normal Android claim."""
import copy,json,os,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex

class JiangExportTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pin=ex.load(ROOT/'ci/golden-world-jiang-content.json')
  cls.apk=Path(os.environ['FENGSHEN_CONTENT_BASE_APK'])
  base=ci.content(cls.apk,cls.pin['iteration']['base'])
  cls.payload=ex.export_from_base(base,cls.pin['iteration']['provenance'],cls.pin)
  parent=ex.load(ROOT/'ci/golden-world-reference-repair-content.json')
  cls.parent=ex.export_from_base(base,parent['iteration']['provenance'],parent)
  cls.reader=ex.iteration_reader()
  cls.evidence=ex.load(ROOT/'game-data/provenance/world-jiang-invitation.json')
  cls.proof=ex.load(ROOT/cls.evidence['exitAdjacencyEvidence']['path'])
 def test_exact_export_keeps_existing_media_services_and_adds_only_three_dependencies(self):
  self.assertEqual(self.pin['manifestSha256'],ex.digest(self.payload['manifest.json']))
  self.assertEqual(388,len(self.payload))
  old=json.loads(self.parent['scene.json']);new=json.loads(self.payload['scene.json'])
  self.assertEqual({7,121,142},{x['id']for x in new['maps']}-{x['id']for x in old['maps']})
  self.assertEqual(72,len(new['maps']))
  for key in ('initialPlayer','shops','inns','clinics','serviceBindings','ferries','freeBoat','mapArrivals','sceneStories'):
   self.assertEqual(old.get(key),new.get(key),key)
  for name,raw in self.parent.items():
   if not name.endswith('.json'):self.assertEqual(raw,self.payload[name],name)
  self.assertEqual({'evidence':ex.load(ROOT/self.pin['iteration']['provenance'])['jiangCapabilityEvidence'],
      'id':'rom.event.7.21','kingNpcId':'rom.npc.121.3'},new['originalJiangJoin'])
 def test_joined_growth_requires_real_loader_table_and_cap_evidence(self):
  recipe=ex.load(ROOT/self.pin['iteration']['provenance'])
  table=next(t for t in json.loads(self.payload['combat.json'])['characterGrowth']if t['owner']=='jiangziya')
  self.assertEqual('game-data/provenance/world-jiang-invitation.json',table['evidence'])
  self.assertEqual(table['evidence'],table['limitEvidence'])
  for field in ('evidence','limitEvidence'):
   for value in (None,'game-data/provenance/unrelated.json'):
    overlay=copy.deepcopy(recipe['combatOverlay'])
    if value is None:overlay['characterGrowth'][0].pop(field)
    else:overlay['characterGrowth'][0][field]=value
    with self.subTest(field=field,value=value),self.assertRaisesRegex(ValueError,'loader-facing table and cap evidence'):
     ex.extend_world_characters(self.reader,json.loads(self.parent['scene.json']),
       json.loads(self.parent['combat.json']),recipe['additionalCharacters'],overlay)
 def test_operable_hit_lookups_match_native_and_missing_or_forged_extensions_are_rejected(self):
  recipe=ex.load(ROOT/self.pin['iteration']['provenance'])
  ex.validate_world_jiang_hit_extensions(self.reader,recipe)
  scene=json.loads(self.payload['scene.json']);hits=json.loads(self.payload['combat.json'])['physicalRules']['weaponHitThreshold']
  for item in scene['items']:
   if item['category']=='weapon'and item.get('equipment')and item['equipment'].get('operationEnabled',True):
    self.assertEqual(self.reader.read(9,0x9b41+item['originalId'])[0],hits[str(item['originalId'])])
  for kind in ('missing7','missing44','threshold','address','hash'):
   bad=copy.deepcopy(recipe);entries=bad['combatOverlay']['weaponHits']
   if kind.startswith('missing'):entries[:]=[v for v in entries if v['originalId']!=int(kind[7:])]
   elif kind=='threshold':entries[-1]['threshold']+=1
   elif kind=='address':entries[-1]['source']['cpuAddress']-=1
   else:entries[-1]['source']['sha256']='0'*64
   with self.subTest(kind=kind),self.assertRaises(ValueError):ex.validate_world_jiang_hit_extensions(self.reader,bad)
 def test_only_five_tested_source_cells_are_runtime_edges_and_failed_lefts_stay_inert(self):
  scene=json.loads(self.payload['scene.json'])
  edges=[x for x in scene['exits']if x['fromMapId']==142 and x['toMapId']==16]
  self.assertEqual([[x,42]for x in range(13,18)],[x['trigger']for x in edges])
  for row in edges:
   self.assertEqual('EDGE',row['triggerMode']);self.assertEqual('DOWN',row['direction'])
   self.assertTrue(row['resetEncounterSteps']);self.assertEqual([42,78],row['spawn'])
  self.assertFalse(any((x['fromMapId'],x['trigger'])in((121,[1,29]),(142,[3,10]))for x in scene['exits']))
  self.assertEqual('ffff102a4e',ex.checked_span(self.reader,self.proof['originalSouthRecord']['source']).hex())
 def test_adjacency_hash_unknown_cells_wrong_direction_or_removed_failures_are_rejected(self):
  for kind in ('hash','row43','unseenX','direction','left','removeFailure','sourceRecord'):
   evidence=copy.deepcopy(self.evidence);proof=copy.deepcopy(self.proof)
   if kind=='hash':evidence['exitAdjacencyEvidence']['sha256']='0'*64
   elif kind=='row43':evidence['exits'][-1]['trigger']=[17,43]
   elif kind=='unseenX':evidence['exits'][-1]['trigger']=[18,42]
   elif kind=='direction':evidence['exits'][-1]['direction']='LEFT'
   elif kind=='left':evidence['exits'].append(copy.deepcopy(evidence['inactiveExitRecords'][0]))
   elif kind=='removeFailure':proof['failedDepartures']=[]
   else:proof['originalSouthRecord']['source']['sha256']='0'*64
   original=ex.load;path=ROOT/evidence['exitAdjacencyEvidence']['path']
   with patch.object(ex,'load',side_effect=lambda p:proof if Path(p)==path else original(p)):
    with self.subTest(kind=kind),self.assertRaises(ValueError):ex.validate_world_jiang_exit_adjacency(self.reader,evidence)
 def test_strict_restore_to_empty_directory_matches_every_exported_byte(self):
  with tempfile.TemporaryDirectory(prefix='fengshen-c61-empty-')as tmp,patch.object(ci,'CONFIG',self.pin):
   destination=Path(tmp)/'development';ci.restore(self.apk,destination,next_code=85)
   self.assertEqual(set(self.payload),{p.relative_to(destination).as_posix()for p in destination.rglob('*')if p.is_file()})
   for name,raw in self.payload.items():self.assertEqual(raw,(destination/name).read_bytes(),name)

if __name__=='__main__':unittest.main()
