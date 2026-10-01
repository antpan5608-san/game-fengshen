import json
import sqlite3
import sys
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.common import ROOT,load,sha
from forensics.importer import decode_layer,xml_tree

class ImportIntegrityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=load(ROOT/'game-data/raw/reference-project/dataset.json');cls.manifest=load(ROOT/'game-data/raw/reference-project/manifest.json');cls.reference=Path(cls.manifest['sourceRoot'])
    def test_all_twenty_tables_every_field_every_value_roundtrip(self):
        dbfile=self.reference/'Resources/res/MainData';before=sha(dbfile)
        with sqlite3.connect(dbfile.as_uri()+'?mode=ro',uri=True) as db:
            db.row_factory=sqlite3.Row
            for name, in db.execute("SELECT name FROM sqlite_master WHERE type='table'"):
                with self.subTest(table=name):
                    rows=[dict(x) for x in db.execute('SELECT * FROM "'+name+'" ORDER BY rowid')]
                    imported=[x['data'] for x in self.raw['records'] if x['table']==name]
                    self.assertEqual(rows,imported)
                    self.assertEqual(load(ROOT/'game-data/raw/reference-project/tables'/f'{name}.json')['rows'],rows)
        self.assertEqual(sha(dbfile),before);self.assertEqual(before,self.manifest['databaseSha256']);self.assertEqual(len(self.raw['records']),7105)
    def test_every_tmx_xml_tree_gid_and_hash_preserved(self):
        self.assertEqual(len(self.raw['maps']),259)
        for m in self.raw['maps']:
            with self.subTest(map=m['id']):
                path=self.reference/m['sourceFile'];xml=ET.parse(path).getroot();self.assertEqual(m['sha256'],sha(path));self.assertEqual(m['xmlTree'],xml_tree(xml))
                self.assertEqual([x['gids'] for x in m['layers']],[decode_layer(l.find('data')) for l in xml.findall('layer')])
    def test_every_entity_asset_and_tmx_has_resolvable_source(self):
        sources={x['id']:x for x in load(ROOT/'game-data/provenance/reference-project.json')['records']}
        for x in self.raw['records']+self.raw['maps']+self.raw['assets']:
            self.assertTrue(x['sourceRefs'])
            for s in x['sourceRefs']:
                self.assertIn(s,sources);self.assertFalse(sources[s]['originalVerified']);self.assertEqual(sources[s]['source'],'REFERENCE_PROJECT')
    def test_canonical_stays_empty_while_no_original_input(self):
        d=load(ROOT/'game-data/canonical/baseline.json');self.assertFalse(d['runtimeReady'])
        if not list((ROOT/'reference/rom').glob('*.nes')):
            self.assertEqual(sum(len(v) for v in d['entities'].values()),0)

if __name__=='__main__':unittest.main()
