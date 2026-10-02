"""Rebuild this route from the reviewed APK/ROM; reject bad data before packaging."""
import io,json,os,sys,unittest,struct,zlib
from PIL import Image
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter

class NanhaiExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Historical golden coverage stays fixed when the live iteration acquires a newer base.
        cls.pin=json.loads((ci.ROOT/'ci/golden-nanhai-content.json').read_text(encoding='utf-8'))
        path=Path(os.environ.get('FENGSHEN_GOLDEN_BASE_APK',str(ci.ROOT/'artifacts/published/fengshen-remake-v24-release.apk')))
        if not path.is_file():raise RuntimeError('Reviewed immutable base APK required for Nanhai export tests')
        if ci.sha(path.read_bytes())!=cls.pin['iteration']['base']['apkSha256']:raise ValueError('Wrong base APK')
        cls.base=ci.content(path,cls.pin['iteration']['base']);cls.proof=cls.pin['iteration']['provenance']
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
    def test_repeatable_export_preserves_all_base_media(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        for name,raw in self.base.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
    def test_portable_png_is_decodable_and_preserves_reviewed_pixels(self):
        evidence=json.loads((ci.ROOT/self.proof).read_text(encoding='utf-8'))
        for name,recipe in evidence['graphics'].items():
            image=Image.open(io.BytesIO(self.result[name])).convert('RGBA')
            self.assertEqual((recipe['width'],recipe['height']),image.size)
            self.assertEqual(recipe['rgbaSha256'],ci.sha(image.tobytes()),name)
        # Includes a >64KiB scanline stream: multiple stored DEFLATE blocks.
        image=Image.new('RGBA',(256,256),(11,22,33,44))
        raw=exporter.deterministic_rgba_png(image)
        self.assertEqual(image.tobytes(),Image.open(io.BytesIO(raw)).convert('RGBA').tobytes())
        offset=8;types=[];idat=b''
        while offset<len(raw):
            size=struct.unpack('>I',raw[offset:offset+4])[0];kind=raw[offset+4:offset+8]
            data=raw[offset+8:offset+8+size];crc=struct.unpack('>I',raw[offset+8+size:offset+12+size])[0]
            self.assertEqual(zlib.crc32(kind+data)&0xffffffff,crc)
            types.append(kind)
            if kind==b'IDAT':idat+=data
            offset+=12+size
        self.assertEqual([b'IHDR',b'IDAT',b'IEND'],types)
        self.assertEqual(b''.join(b'\x00'+bytes((11,22,33,44))*256 for _ in range(256)),zlib.decompress(idat))
        # The new exporter cannot fall back to a platform image encoder.
        with patch.object(Image.Image,'save',side_effect=AssertionError('Platform encoder used')):
            self.assertEqual(raw,exporter.deterministic_rgba_png(image))
    def test_changed_reviewed_pixels_are_rejected(self):
        evidence=json.loads((ci.ROOT/self.proof).read_text(encoding='utf-8'))
        evidence['graphics']['enemy-137.png']['rgbaSha256']='0'*64
        with patch.object(exporter,'load',side_effect=lambda path: evidence if Path(path).resolve()==(ci.ROOT/self.proof).resolve() else json.loads(Path(path).read_text(encoding='utf-8'))):
            with self.assertRaisesRegex(ValueError,'RGBA pixels'):exporter.export_from_base(self.base,self.proof,self.pin)
    def test_real_contiguous_route_and_original_encounters(self):
        scene=json.loads(self.result['scene.json']);self.assertEqual({114,16,0,17,18,19,25,97},{m['id'] for m in scene['maps']})
        exits={(x['fromMapId'],tuple(x['trigger']),x['toMapId'],tuple(x['spawn'])) for x in scene['exits']}
        for expected in [(16,(199,130),25,(39,42)),(25,(39,42),16,(199,130)),(25,(29,44),97,(15,29)),(97,(15,29),25,(29,44))]:self.assertIn(expected,exits)
        self.assertEqual(4,len([x for x in scene['exits'] if x.get('resetEncounterSteps')]))
        battle=json.loads(self.result['combat.json']);self.assertEqual([12,13],[len(z['groups']) for z in battle['zones']])
        self.assertEqual('HIGH',battle['zones'][1]['randomGate']);self.assertEqual(245,battle['zones'][1]['randomThreshold'])
        self.assertEqual('rom.event.97.39.1',battle['bosses'][0]['flagId'])
        self.assertEqual([64,0],next(g for g in battle['presentation']['graphics'] if g['enemyId']==137)['origin'])
        self.assertEqual([120,16,13,60,100],[next(e for e in battle['enemies'] if e['id']==137)[k] for k in ['hp','attack','defense','experienceReward','moneyReward']])
    def test_full_foot_areas_and_only_unimplemented_region_boundary(self):
        sea=json.loads(self.result['scene25.json']);palace=json.loads(self.result['scene97.json'])
        self.assertEqual([0,2],sea['walkableClasses']);self.assertEqual([[2,0,30,22],[31,0,63,35]],sea['unavailableRegions'])
        self.assertEqual([],palace['unavailableRegions']);self.assertIn(44*64+29,sea['enabledCells'])
        self.assertGreater(len(sea['enabledCells']),500);self.assertGreater(len(palace['enabledCells']),200)
        before=json.loads(self.base['scene16.json']);after=json.loads(self.result['scene16.json'])
        self.assertTrue(set(before['enabledCells']).issubset(after['enabledCells']))
    def test_wrong_base_target_rom_span_or_enemy_cannot_export(self):
        with self.assertRaisesRegex(ValueError,'base content'):
            exporter.export_from_base(dict(self.base,**{'manifest.json':b'wrong'}),self.proof,self.pin)
        with self.assertRaisesRegex(ValueError,'target pin'):
            exporter.export_from_base(self.base,self.proof,dict(self.pin,manifestSha256='0'*64))
        original=json.loads((ci.ROOT/self.proof).read_text(encoding='utf-8'))
        for kind in ['enemy','exit','tile','origin','counter','rom']:
            evidence=json.loads(json.dumps(original))
            if kind=='enemy':evidence['combatOverlay']['enemies'][0]['hp']+=1
            elif kind=='exit':evidence['exits'][0]['spawn']=[0,0]
            elif kind=='tile':evidence['graphics']['enemy-137.png']['tiles'][0]['sha256']='0'*64
            elif kind=='origin':next(g for g in evidence['combatOverlay']['graphics'] if g['enemyId']==137)['origin']=[112,72]
            elif kind=='counter':evidence['exits'][0]['resetEncounterSteps']=False
            else:evidence['romSha256']='0'*64
            with patch.object(exporter,'load',side_effect=lambda path: evidence if Path(path).resolve()==(ci.ROOT/self.proof).resolve() else json.loads(Path(path).read_text(encoding='utf-8'))):
                with self.assertRaises(ValueError):exporter.export_from_base(self.base,self.proof,self.pin)
    def test_manifest_hashes_every_file_and_keeps_herb_rule(self):
        manifest=json.loads(self.result['manifest.json']);self.assertEqual(set(self.result)-{'manifest.json'},set(manifest['files']))
        for name,expected in manifest['files'].items():self.assertEqual(expected,ci.sha(self.result[name]))
        herb=next(x for x in json.loads(self.result['scene.json'])['items'] if x['id']=='rom.medicine.0')
        self.assertEqual(50,herb['herbUse']['healHp']);self.assertTrue(herb['herbUse']['consumeAtFullHp'])
        unknown=next(x for x in json.loads(self.result['scene.json'])['items'] if x['id']=='rom.medicine.7')
        self.assertNotIn('herbUse',unknown);self.assertEqual('PROVISIONAL_REFERENCE',unknown['source']['confidence'])
if __name__=='__main__':unittest.main()
