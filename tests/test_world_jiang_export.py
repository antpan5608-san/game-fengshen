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
