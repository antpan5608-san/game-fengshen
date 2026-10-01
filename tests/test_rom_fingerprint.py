import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from test_rom import fixture
from forensics import rom
from forensics.validator import Report,validate_provenance
from forensics.comparison import verified_source

class FingerprintTests(unittest.TestCase):
    def test_fingerprint_independent_of_filename(self):
        with tempfile.TemporaryDirectory() as temp:
            a=Path(temp)/'claimed-original.nes';b=Path(temp)/'different-version.NES'
            data=fixture();a.write_bytes(data);b.write_bytes(data)
            first,second=rom.analyze(a),rom.analyze(b)
            self.assertEqual(first['fingerprint'],second['fingerprint'])
            self.assertEqual(first['fingerprint']['prgSha256'],hashlib.sha256(bytes(16384)).hexdigest())
            self.assertEqual(first['fingerprint']['chrSha256'],hashlib.sha256(bytes(8192)).hexdigest())
            self.assertFalse(first['originalVersionVerified'])
    def test_header_change_differs_file_but_not_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'fixture.nes';data=bytearray(fixture());p.write_bytes(data);before=rom.analyze(p)
            data[6]^=1;p.write_bytes(data);after=rom.analyze(p)
            self.assertNotEqual(before['fingerprint']['id'],after['fingerprint']['id'])
            self.assertEqual(before['fingerprint']['prgChrSha256'],after['fingerprint']['prgChrSha256'])
    def test_recursive_case_insensitive_multi_rom_scan(self):
        with tempfile.TemporaryDirectory() as temp,patch('forensics.rom.save'):
            root=Path(temp);(root/'nested').mkdir();(root/'one.nes').write_bytes(fixture());(root/'nested'/'one.NES').write_bytes(fixture())
            r=rom.scan_roms(root);self.assertEqual(len(r['roms']),2)
            self.assertEqual({x['relativePath'] for x in r['roms']},{'one.nes','nested/one.NES'})
            self.assertEqual(len({x['fingerprint']['id'] for x in r['roms']}),1)
    def test_empty_scan_and_invalid_rom_never_invent_payload(self):
        with tempfile.TemporaryDirectory() as temp,patch('forensics.rom.save'):
            root=Path(temp);self.assertEqual(rom.scan_roms(root)['status'],'WAITING_FOR_ROM')
            p=root/'invalid.nes';p.write_bytes(b'not a ROM');r=rom.analyze(p)
            self.assertEqual(r['status'],'INVALID');self.assertIsNone(r['fingerprint']['prgSha256']);self.assertIsNone(r['fingerprint']['prgChrSha256'])

class RomEvidenceRequirementTests(unittest.TestCase):
    def test_gameplay_without_rom_cannot_verify_original(self):
        gameplay={'id':'source.gameplay','source':'GAMEPLAY_VERIFIED','confidence':'VERIFIED','originalVerified':False,
          'licenseStatus':'UNKNOWN','locator':{'path':'synthetic-test.mp4','meaning':'test only'},'evidenceRefs':[],'note':'Synthetic test'}
        claim={'id':'source.claim','source':'MANUAL','confidence':'VERIFIED','originalVerified':True,
          'licenseStatus':'UNKNOWN','locator':{},'evidenceRefs':['source.gameplay'],'note':'Synthetic test'}
        r=Report('test');idx=validate_provenance({'schemaVersion':1,'records':[gameplay,claim]},r)
        self.assertIn('UNSUPPORTED_ORIGINAL_VERIFICATION',r.finish()['issuesByCode'])
        self.assertFalse(verified_source('source.claim',idx,'a'*64))

if __name__=='__main__':unittest.main()
