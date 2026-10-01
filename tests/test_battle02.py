import unittest,json,hashlib
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
class Battle02EvidenceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.package=ROOT/'game-data/packages/development/opening-segment-001-c9'
        if not cls.package.exists():raise unittest.SkipTest('Local derived package absent; no ROM download')
        cls.combat=json.loads((cls.package/'combat.json').read_text(encoding='utf-8'))
        cls.evidence=json.loads((ROOT/'game-data/provenance/battle02.json').read_text(encoding='utf-8'))
    def test_three_independent_graphics_have_rom_tile_chain(self):
        graphics=self.combat['presentation']['graphics'];self.assertEqual({g['enemyId'] for g in graphics},{1,2,3})
        roms=list((ROOT/'reference/rom').glob('*.nes'))
        if not roms:self.skipTest('No local ROM; never download it')
        rom=roms[0].read_bytes()
        self.assertEqual(hashlib.sha256(rom).hexdigest(),self.combat['romSha256'])
        for g in graphics:
            src=g['source'];self.assertTrue(src['pixelMatch'])
            im=Image.open(self.package/g['asset']).convert('RGB')
            sample=Image.open(ROOT/src['capture']).convert('RGB');x,y,w,h=src['observedRect']
            self.assertEqual(im.tobytes(),sample.crop((x,y,x+w,y+h)).tobytes())
            for tile in src['tiles']:
                self.assertEqual(hashlib.sha256(rom[tile['offset']:tile['offset']+tile['length']]).hexdigest(),tile['sha256'])
    def test_escape_formula_matches_observed_success_and_failures(self):
        rows=self.evidence['escape']['samples'];self.assertEqual(len(rows),6)
        for r in rows:
            b=r['randomByte'];self.assertEqual((5*b+2+(b>>7)+((b>>6)&1))&255,r['transformed'])
            self.assertEqual(r['transformed']<r['threshold'],r['success'])
        self.assertEqual(sum(r['success'] for r in rows),1)
        self.assertEqual(self.evidence['escape']['success']['grace06E2'],0)
    def test_defeat_is_opening_reset_not_checkpoint_restore(self):
        after=self.evidence['defeat']['after']
        self.assertEqual((after['mapId'],after['cell'],after['hp'],after['experience'],after['money']),(114,[8,21],20,0,0))
        self.assertEqual(self.combat['defeat']['kind'],'RESET_OPENING_STATE')
        for path,expected in self.evidence['captures'].items():
            self.assertEqual(hashlib.sha256((ROOT/path).read_bytes()).hexdigest(),expected)
    def test_new_package_hashes_and_original_group_instance_count(self):
        m=json.loads((self.package/'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(m['version'],'opening-segment-001-c9')
        for file,expected in m['files'].items():self.assertEqual(hashlib.sha256((self.package/file).read_bytes()).hexdigest(),expected)
        self.assertEqual(len(self.combat['groups']),19)
        self.assertTrue(any(len(g['entities'])>3 for g in self.combat['groups']))
        self.assertTrue(any(len({e['enemyId'] for e in g['entities']})<len(g['entities']) for g in self.combat['groups']))
