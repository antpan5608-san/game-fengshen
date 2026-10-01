"""Synthetic fixtures are authored here; optional local goldens never download a ROM."""
import hashlib
import sys
import unittest
import tempfile
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.common import ROOT,load
from forensics.fengshen246 import (Reader,SHA256,cpu_offset,glyph_pixels,decode_tokens,
    extract_map,extract_enemy,extract_text,extract_npcs,extract_growth_candidates,extract_opening_encounter,verify_enemy_runtime,verify_opening_viewport,digest)
from forensics.validator import validate_original_artifacts

def fixture():
    data=bytearray(16+0x100000);data[:16]=b'NES\x1a\x20\x40\x62\xf0'+bytes(8)
    return data

def write(data,module,address,values):
    start=cpu_offset(module,address,len(values));data[start:start+len(values)]=values

def word(data,module,address,value):write(data,module,address,value.to_bytes(2,'little'))

class ScopedResearchTests(unittest.TestCase):
    def test_evidence_audit_detects_rom_and_range_tampering(self):
        with tempfile.TemporaryDirectory() as directory,patch('forensics.validator.ROOT',Path(directory)):
            p=Path(directory)/'input.nes';p.write_bytes(b'abcdefgh')
            source={'id':'source.test','source':'ORIGINAL_ROM','confidence':'VERIFIED','locator':
              {'path':'input.nes','romSha256':digest(p.read_bytes()),'offset':2,'length':3,'sha256':digest(b'cde')}}
            self.assertEqual(validate_original_artifacts({'records':[source]})['status'],'PASS')
            source['locator']['sha256']=digest(b'wrong')
            self.assertIn('EVIDENCE_HASH_MISMATCH',validate_original_artifacts({'records':[source]})['issuesByCode'])
            p.write_bytes(b'changed')
            self.assertIn('ROM_FINGERPRINT_MISMATCH',validate_original_artifacts({'records':[source]})['issuesByCode'])
    def test_missing_private_evidence_is_unknown_and_escape_rejected(self):
        with tempfile.TemporaryDirectory() as directory,patch('forensics.validator.ROOT',Path(directory)):
            source={'id':'source.test','source':'GAMEPLAY_VERIFIED','confidence':'VERIFIED','locator':{'path':'missing.png'}}
            result=validate_original_artifacts({'records':[source]});self.assertEqual(result['counts'],{'UNKNOWN':1})
            source['locator']['path']='../escape.png'
            self.assertIn('EVIDENCE_PATH_ESCAPE',validate_original_artifacts({'records':[source]})['issuesByCode'])
    def test_profile_rejects_filename_or_header_only_identity(self):
        with self.assertRaisesRegex(ValueError,'fingerprint'):Reader(fixture())
    def test_cpu_mapping_requires_bank_and_checks_bounds(self):
        self.assertEqual(cpu_offset(8,0xd345),0x45355)
        for module,addr,length in [(16,0x8000,1),(0,0x7fff,1),(0,0xffff,2)]:
            with self.assertRaises(ValueError):cpu_offset(module,addr,length)
    def test_charset_two_planes_and_four_tile_order_golden(self):
        data=bytearray(4096);data[10*16]=0x80;data[10*16+8]=0x01
        data[11*16+7]=0x01;data[12*16]=0x80;data[13*16+7+8]=0x01
        low=glyph_pixels(data,0,0);high=glyph_pixels(data,0,128)
        self.assertEqual([(y,x) for y in range(16) for x in range(16) if low[y][x]],[(0,0),(7,15),(8,0)])
        self.assertEqual([(y,x) for y in range(16) for x in range(16) if high[y][x]],[(0,7),(15,15)])
        with self.assertRaises(ValueError):glyph_pixels(data,0,0xc0)
        with self.assertRaises(ValueError):glyph_pixels(b'',0,0)
    def test_dialogue_controls_unknown_and_terminator(self):
        result=decode_tokens(bytes([0x24,0xa4,0xc2,0x80,0x44,0xc3,0x24]))
        self.assertEqual(result['text'],'哪吒\n做<44>');self.assertEqual(result['unknownCodes'],[0x44])
        self.assertEqual(result['consumed'],6)
        with self.assertRaises(ValueError):decode_tokens(b'\x24\xa4')
    def test_text_pointer_bounded_and_undecoded_by_default(self):
        data=fixture();word(data,3,0x8a62,0x9000);word(data,3,0x9000,0xfffd);write(data,3,0xfffd,b'\x24\xa4\xc3')
        result=extract_text(Reader(data,False),0,0)
        self.assertIsNone(result['text']);self.assertEqual(result['range']['length'],3)
        write(data,3,0xffff,b'\0')
        with self.assertRaisesRegex(ValueError,'terminator'):extract_text(Reader(data,False),0,0)
    def test_map_chunk_boundary_16_by_15_and_partial_last_chunk(self):
        data=fixture();mapid=114
        word(data,0,0xdcfc+mapid*2,0xdff6);write(data,0,0xdff6,bytes([4,0,2,240,0])) # 33 x 16
        word(data,0,0xdb9e+2*mapid,0xea93);write(data,0,0xe22b+mapid,b'\x07')
        for address,value in [(0xdb76,0x8100),(0xdb86,0x8500),(0xdb96,0x8600)]:word(data,0,address,value)
        for index in range(6):
            word(data,0,0xea93+index*2,0x9000+index*240);write(data,7,0x9000+index*240,bytes([index+1])*240)
        m=extract_map(Reader(data,False),mapid)
        self.assertEqual((m['width'],m['height']),(33,16))
        self.assertEqual(m['grid'][0],[1]*16+[2]*16+[3])
        self.assertEqual(m['grid'][14],m['grid'][0]);self.assertEqual(m['grid'][15],[4]*16+[5]*16+[6])
        self.assertEqual(len(m['chunks']),6);self.assertFalse(m['gameplayVerified'])
    def test_npc_terminator_and_bound(self):
        data=fixture();word(data,8,0xb311+228,0xd345)
        write(data,8,0xd345,bytes(range(14))+b'\xff')
        result=extract_npcs(Reader(data,False));self.assertEqual(len(result['records']),1);self.assertEqual(result['range']['length'],15)
        write(data,8,0xd345,bytes(14*64))
        with self.assertRaisesRegex(ValueError,'terminator'):extract_npcs(Reader(data,False))
    def test_enemy_unsigned_little_endian_and_runtime_mismatch(self):
        data=fixture();word(data,1,0xea5c,0xec00)
        write(data,1,0xec00,bytes([1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16]))
        enemy=extract_enemy(Reader(data,False),2)
        self.assertEqual([enemy[k] for k in ['hp','attack','defense','experienceReward','moneyReward']],[513,1027,1541,2055,2569])
        self.assertFalse(verify_enemy_runtime(enemy,bytes(2048),2)['passed'])

class LocalRomGoldenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        paths=[p for p in (ROOT/'reference/rom').rglob('*') if p.is_file() and p.suffix.lower()=='.nes']
        paths=[p for p in paths if digest(p.read_bytes())==SHA256]
        if not paths:raise unittest.SkipTest('Optional local ROM golden: no matching private ROM; no download attempted')
        cls.reader=Reader(paths[0].read_bytes())
    def test_dialogue_golden_unmodified_text(self):
        d=extract_text(self.reader,124,10,True)
        self.assertEqual(d['range']['offset'],0x1b994);self.assertEqual(d['range']['length'],71)
        self.assertEqual(d['text'],'『哪吒！都是你做的好事！惹火四海龍王，連降三個月豪雨，害得陳塘關附近水患二尺，身為總兵官的我，要如何向全村交代？你走吧！當我沒你這個兒子。』')
    def test_map_geometry_and_observed_patch_golden(self):
        m=extract_map(self.reader,114);self.assertEqual((m['width'],m['height']),(32,30))
        self.assertEqual(m['header']['offset'],0x6006);self.assertEqual(len(m['chunks']),4)
        p=ROOT/'private-derived/npc-probe/frame-0450-ppu.bin'
        if p.exists():
            self.assertTrue(verify_opening_viewport(m,p.read_bytes())['passed'])
            altered=bytearray(p.read_bytes());altered[0x2802]^=1
            self.assertFalse(verify_opening_viewport(m,altered)['passed'])
    def test_enemy_golden_against_independent_runtime_snapshot(self):
        for enemy_id,hp,attack,defense,exp,money,slot in [(2,10,7,2,2,1,2),(3,11,9,3,3,2,4)]:
            e=extract_enemy(self.reader,enemy_id)
            self.assertEqual([e[k] for k in ['hp','attack','defense','experienceReward','moneyReward']],[hp,attack,defense,exp,money])
            p=ROOT/'private-derived/battle-probe/frame-1980-sram.bin'
            if p.exists():self.assertTrue(verify_enemy_runtime(e,p.read_bytes(),slot)['passed'])
    def test_opening_encounter_complete_group_table_and_runtime_sample(self):
        e=extract_opening_encounter(self.reader)
        self.assertEqual(e['romSha256'],SHA256)
        self.assertEqual((e['mapId'],e['zoneId']),(16,0))
        self.assertEqual(e['rectangles'][0],[193,128,216,151])
        self.assertEqual(len(e['groups']),19)
        self.assertEqual({x['enemyId'] for g in e['groups'] for x in g['entities']},{1,2,3})
        self.assertEqual(e['groups'][11]['entities'],[
            {'slot':2,'sourceType':3,'enemyId':2},{'slot':4,'sourceType':4,'enemyId':3}])
        self.assertEqual(e['groups'][11]['rawHex'],'0303050400')
        self.assertEqual(e['encounterGate']['randomByteThreshold'],16)
        for group in e['groups']:
            span=group['range'];self.assertEqual(digest(self.reader.data[span['offset']:span['offset']+span['length']]),span['sha256'])
        capture=ROOT/'private-derived/battle01-state/frame-1820-sram.bin'
        if capture.exists():
            s=capture.read_bytes()
            self.assertEqual([(slot,s[0x177+slot]) for slot in (2,4)],[(2,2),(4,3)])
    def test_provenance_ranges_match_real_rom_bytes(self):
        p=ROOT/'game-data/provenance/original.json'
        if not p.exists():self.skipTest('No generated evidence manifest')
        for source in load(p)['records']:
            if source['source']!='ORIGINAL_ROM':continue
            loc=source['locator'];self.assertEqual(loc['romSha256'],SHA256)
            self.assertEqual(digest(self.reader.data[loc['offset']:loc['offset']+loc['length']]),loc['sha256'])
    def test_growth_mixed_bank_mapping_and_unresolved_threshold_golden(self):
        growth=extract_growth_candidates(self.reader)
        self.assertEqual([len(g['rows']) for g in growth['groups']],[80]*4)
        first=growth['groups'][0]
        self.assertEqual(first['growthRange']['offset'],0x5b370)
        self.assertEqual(first['thresholdRange']['offset'],0x5bc30)
        self.assertEqual(first['rows'][1]['cumulativeExpCandidate'],12)
        self.assertEqual(first['rows'][1]['hpDeltaCandidate'],3)
        self.assertNotEqual(first['rows'][1]['cumulativeExpCandidate'],7)
