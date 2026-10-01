import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.common import DOMAINS
from forensics.comparison import compare,markers

def inputs():
    raw={'records':[{'id':'reference.object.1','table':'object','legacyId':1,'sourceRefs':['source.reference.object.1'],'data':{'id':1,'name':'Potion','price':10}}],
      'maps':[],'assets':[],'domains':{d:[] for d in DOMAINS if d!='assets'}};raw['domains']['items']=['reference.object.1']
    leaf={'id':'source.rom.bytes','source':'ORIGINAL_ROM','confidence':'VERIFIED','originalVerified':False,'licenseStatus':'UNKNOWN',
      'locator':{'romSha256':'a'*64,'offset':100,'length':32,'meaning':'Synthetic fixture byte range'},'evidenceRefs':[],'note':'SYNTHETIC'}
    verified={'id':'source.original.item','source':'MANUAL','confidence':'VERIFIED','originalVerified':True,'licenseStatus':'UNKNOWN',
      'locator':{'path':'tests/test_comparison.py'},'evidenceRefs':['source.rom.bytes'],'note':'SYNTHETIC proof, not actual game data'}
    source={'id':'source.reference.object.1','source':'REFERENCE_PROJECT','confidence':'LOW','originalVerified':False,'licenseStatus':'UNKNOWN','locator':{},'evidenceRefs':[],'note':'SYNTHETIC'}
    sources={'schemaVersion':1,'records':[source,leaf,verified]}
    original={'schemaVersion':1,'baseRomSha256':'a'*64,'completeDomains':[],'coverageSourceRefs':{},'records':[
      {'domain':'items','id':'original.item','referenceId':'reference.object.1','complete':True,'comparable':copy.deepcopy(raw['records'][0]['data']),'sourceRefs':['source.original.item']}]}
    return raw,sources,original

class ComparisonTests(unittest.TestCase):
    def test_missing_rom_is_unknown_not_reference_only(self):
        raw,sources,_=inputs();r=compare(raw,sources);self.assertEqual(r['records'][0]['status'],'UNKNOWN');self.assertFalse(r['records'][0]['originalVerified'])
    def test_match_modified_partial(self):
        raw,sources,o=inputs();self.assertEqual(compare(raw,sources,o)['records'][0]['status'],'MATCH')
        o['records'][0]['comparable']['price']=20;r=compare(raw,sources,o);self.assertEqual(r['records'][0]['status'],'MODIFIED');self.assertEqual(r['records'][0]['differences'][0]['field'],'/price')
        o['records'][0].update(complete=False,comparable={'name':'Potion'});r=compare(raw,sources,o);self.assertEqual(r['records'][0]['status'],'LIKELY_MATCH');self.assertFalse(r['records'][0]['originalVerified'])
    def test_no_coverage_no_reference_only(self):
        raw,sources,o=inputs();o['records']=[];o['completeDomains']=['items'];self.assertEqual(compare(raw,sources,o)['records'][0]['status'],'UNKNOWN')
        o['coverageSourceRefs']={'items':['source.original.item']};self.assertEqual(compare(raw,sources,o)['records'][0]['status'],'REFERENCE_ONLY')
    def test_rom_only_explicit_and_verified(self):
        raw,sources,o=inputs();o['records'][0]['referenceId']=None;self.assertEqual(compare(raw,sources,o)['records'][-1]['status'],'ROM_ONLY')
        o['records'][0]['sourceRefs']=['source.reference.object.1'];self.assertEqual(compare(raw,sources,o)['records'][-1]['status'],'UNKNOWN')
    def test_hash_mismatch_disables_verification(self):
        raw,sources,o=inputs();o['baseRomSha256']='b'*64;self.assertEqual(compare(raw,sources,o)['records'][0]['status'],'UNKNOWN')
    def test_modern_tags_do_not_mutate_raw_or_invent_rom_diff(self):
        raw,sources,o=inputs();raw['records'][0]['data']['name']='VIP购买后获得1000金币';before=copy.deepcopy(raw);r=compare(raw,sources)
        self.assertEqual(raw,before);self.assertEqual(r['records'][0]['status'],'UNKNOWN');self.assertEqual(r['nonOriginalContent']['uniqueSourceRows'],1)
        self.assertIn('NON_ORIGINAL_REFERENCE_CONTENT',r['records'][0]['tags']);self.assertTrue(markers('购买后额外开启一个存档位'))
    def test_ambiguous_mapping_rejected(self):
        raw,sources,o=inputs();o['records'].append(copy.deepcopy(o['records'][0]))
        with self.assertRaises(ValueError):compare(raw,sources,o)

if __name__=='__main__':unittest.main()
