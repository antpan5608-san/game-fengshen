"""Targeted content gates: bad policies/pins cannot mutate an existing export."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
from export_development import export_from_base

class HerbExportTests(unittest.TestCase):
    def setUp(self):
        scene={'version':'old','items':[{'id':'rom.medicine.0','originalId':0,'category':'medicine',
            'source':{'verifiedFields':['name'],'remainingUnknown':['medicine effects/use conditions']}}]}
        self.base={'scene.json':json.dumps(scene).encode(),'scene0.json':b'{"version":"old","grid":[1,2]}',
            'audio.ogg':b'unchanged-audio','tiles.png':b'unchanged-image'}
        self.base['manifest.json']=json.dumps({'schemaVersion':1,'version':'old','files':{k:ci.sha(v) for k,v in self.base.items()}}).encode()
        self.proof='game-data/provenance/town02-herb.json'
        self.pin={'contentVersion':'test-target'}
        self.expected=export_from_base(self.base,self.proof,self.pin,verify_target=False)
        self.pin['manifestSha256']=ci.sha(self.expected['manifest.json'])
    def test_same_input_repeatable_and_unmodified_media_exact(self):
        self.assertEqual(self.expected,export_from_base(self.base,self.proof,self.pin))
        self.assertEqual(self.expected,export_from_base(self.base,self.proof,self.pin))
        for name in ['audio.ogg','tiles.png']:self.assertEqual(self.base[name],self.expected[name])
        self.assertNotIn('herbUse',json.loads(self.base['scene.json'])['items'][0])
    def test_only_scoped_herb_definition_and_version_changed(self):
        herb=json.loads(self.expected['scene.json'])['items'][0]
        self.assertEqual(50,herb['herbUse']['healHp']);self.assertTrue(herb['herbUse']['consumeAtFullHp'])
        self.assertEqual({'version':'test-target','grid':[1,2]},json.loads(self.expected['scene0.json']))
    def test_wrong_target_pin_rejected(self):
        with self.assertRaisesRegex(ValueError,'target pin'):
            export_from_base(self.base,self.proof,dict(self.pin,manifestSha256='0'*64))
    def test_wrong_rom_or_unreviewed_rule_rejected(self):
        proof=json.loads((ci.ROOT/self.proof).read_text())
        for changes in [{'romSha256':'0'*64},{'herbUse':dict(proof['herbUse'],healHp=999)}]:
            with patch('export_development.load',return_value=dict(proof,**changes)):
                with self.assertRaises(ValueError):export_from_base(self.base,self.proof,self.pin)
    def test_absent_herb_and_escaping_provenance_rejected(self):
        missing=dict(self.base,**{'scene.json':b'{"items":[]}'})
        with self.assertRaises(ValueError):export_from_base(missing,self.proof,self.pin)
        with self.assertRaises(ValueError):export_from_base(self.base,'../../outside.json',self.pin)

if __name__=='__main__':unittest.main()
