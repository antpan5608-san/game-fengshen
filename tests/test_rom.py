import hashlib
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics import rom

def fixture(h=None,prg=16384,chr_size=8192,trainer=False,tail=b''):
    h=bytearray(h or (b'NES\x1a'+bytes([1,1])+bytes(10)))
    if trainer:h[6]|=4
    return bytes(h)+(bytes([0xa5])*512 if trainer else b'')+bytes(prg)+bytes(chr_size)+tail

class RomTests(unittest.TestCase):
    def setUp(self):self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.path=self.root/'fixture.nes'
    def tearDown(self):self.temp.cleanup()
    def write(self,data):self.path.write_bytes(data);return self.path
    def test_ines_trainer_battery_mirroring_offsets_hashes(self):
        h=bytearray(b'NES\x1a'+bytes([1,1,0x33,0x10])+bytes(8));data=fixture(h,trainer=True,tail=b'TAIL')
        r=rom.analyze(self.write(data));self.assertEqual(r['mapper'],19);self.assertEqual(r['mirroringDeclared'],'VERTICAL')
        self.assertTrue(r['batteryBackedPersistentMemoryFlag']);self.assertIsNone(r['prgRam']['nonvolatileBytes'])
        self.assertEqual(r['sections']['prg'],{'offset':528,'length':16384});self.assertEqual(r['sections']['trailing']['length'],4)
        self.assertEqual(r['sha256'],hashlib.sha256(data).hexdigest());self.assertEqual(r['sha1'],hashlib.sha1(data).hexdigest());self.assertEqual(r['md5'],hashlib.md5(data).hexdigest())
        self.assertFalse(r['originalVersionVerified'])
    def test_nes2_extended_mapper_linear_sizes_ram(self):
        h=bytearray(b'NES\x1a'+bytes([1,1,0xa2,0xb8,0x32,0,0x76,0x07,2,0,0,0]))
        r=rom.parse_header(fixture(h));self.assertEqual(r['mapper'],0x2ba);self.assertEqual(r['submapper'],3)
        self.assertEqual(r['prgRam']['volatileBytes'],4096);self.assertEqual(r['prgRam']['nonvolatileBytes'],8192);self.assertEqual(r['chrRam']['volatileBytes'],8192);self.assertEqual(r['timingMode'],2)
        self.assertEqual(rom.nes2_size(1,1,16384),257*16384)
    def test_nes2_exponent_multiplier(self):
        h=bytearray(b'NES\x1a'+bytes([57,52,0,8,0,0xff])+bytes(6))
        r=rom.parse_header(fixture(h,prg=49152,chr_size=8192));self.assertEqual(r['prgRomBytes'],49152);self.assertEqual(r['chrRomBytes'],8192)
    def test_chr_ram_no_fabricated_tiles(self):
        data=fixture(prg=16384,chr_size=0);data=data[:5]+b'\x00'+data[6:];self.write(data)
        self.assertEqual(rom.parse_header(data)['chrMode'],'CHR_RAM_INDICATED')
        with self.assertRaises(ValueError):rom.export_tiles(self.path,self.root/'none.png')
        self.assertFalse((self.root/'none.png').exists())
    def test_bad_magic_truncation_zero_prg_archaic(self):
        for bad in [b'bad',b'BAD!'+bytes(32),fixture()[:-1],b'NES\x1a'+bytes(12)]:
            with self.subTest(size=len(bad)),self.assertRaises(ValueError):rom.parse_header(bad)
        b=bytearray(fixture());b[7]=4
        with self.assertRaises(ValueError):rom.parse_header(b)
    def test_dirty_ines_is_declared_not_trusted(self):
        b=bytearray(fixture());b[12:16]=b'Dude';b[6]=8;r=rom.parse_header(b)
        self.assertEqual(r['mapperConfidence'],'LOW');self.assertEqual(r['mirroringDeclared'],'FOUR_SCREEN_OR_MAPPER_SPECIFIC')
    def test_search_wildcards_overlaps_and_section_offsets(self):
        b=bytearray(fixture(trainer=True));b[528:533]=b'\xaa\xaa\xaa\x01\xcc';self.write(b)
        self.assertEqual([m['fileOffset'] for m in rom.search(self.path,'AA AA','prg')['matches']],[528,529])
        self.assertEqual(rom.search(self.path,'AA ?? CC','prg')['matches'],[{'fileOffset':530,'sectionOffset':2,'length':3}])
        with self.assertRaises(ValueError):rom.search(self.path,'?? ??')
    def test_tiles_bitplanes_golden_and_immutable_input(self):
        tile=bytes([0x80]+[0]*7+[0x40]+[0]*7);im=rom.tile_image(tile,1)
        self.assertEqual(im.size,(8,8));self.assertEqual(im.getpixel((0,0)),(85,85,85));self.assertEqual(im.getpixel((1,0)),(170,170,170));self.assertEqual(im.getpixel((2,0)),(0,0,0))
        b=fixture();self.write(b);rom.export_tiles(self.path,self.root/'tiles.png',count=1);self.assertEqual(self.path.read_bytes(),b)
    def test_dump_and_banks_exact_bytes(self):
        b=fixture(trainer=True);self.write(b);rom.dump(self.path,self.root/'derived')
        self.assertEqual((self.root/'derived/prg.bin').read_bytes(),b[528:528+16384]);self.assertEqual((self.root/'derived/trainer.bin').read_bytes(),b[16:528])
        r=rom.banks(self.path,'prg',6000);self.assertEqual([c['length'] for c in r['chunks']],[6000,6000,4384]);self.assertEqual(r['chunks'][0]['entropy'],0)
        self.assertEqual(self.path.read_bytes(),b)
        with self.assertRaises(ValueError):rom.dump(self.path,self.root)
    def test_search_and_export_bounds(self):
        self.write(fixture())
        with self.assertRaises(ValueError):rom.search(self.path,'100')
        with self.assertRaises(ValueError):rom.export_tiles(self.path,self.root/'x.png',offset=-1)
        with self.assertRaises(ValueError):rom.export_tiles(self.path,self.root/'x.png',count=99999)

if __name__=='__main__':unittest.main()
