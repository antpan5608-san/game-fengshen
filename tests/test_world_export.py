"""WORLD-FULL overlays must preserve reviewed gameplay and reject corrupt service inputs."""
import io,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter

class WorldExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-f0-content.json').read_text(encoding='utf-8'))
        cls.path=Path(os.environ.get('FENGSHEN_WORLD_F0_BASE_APK',str(ci.ROOT/'artifacts/mobile-play01/f9-candidate/fengshen-remake-v26-release.apk')))
        if ci.sha(cls.path.read_bytes())!=cls.pin['iteration']['base']['apkSha256']:raise ValueError('Wrong immutable world base')
        cls.base=ci.content(cls.path,cls.pin['iteration']['base']);cls.proof=cls.pin['iteration']['provenance']
        cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
    def test_repeatable_export_and_unchanged_media(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        changed={f'tiles{x["mapId"]}.png' for x in self.evidence['atlasCorrections']}
        for name,raw in self.base.items():
            if not name.endswith('.json') and name not in changed:self.assertEqual(raw,self.result[name],name)
        for name,expected in json.loads(self.result['manifest.json'])['files'].items():self.assertEqual(expected,ci.sha(self.result[name]))
    def test_real_armor_pixels_and_correct_inn_exit(self):
        self.assertEqual(1,len(Image.open(io.BytesIO(self.base['tiles18.png'])).getcolors(65536)))
        armor=Image.open(io.BytesIO(self.result['tiles18.png']))
        self.assertGreater(len(armor.getcolors(65536)),1)
        scene=json.loads(self.result['scene.json']);inn=json.loads(self.result['scene22.json'])
        edges={(x['fromMapId'],tuple(x['trigger']),x['toMapId'],tuple(x['spawn'])) for x in scene['exits']}
        self.assertIn((0,(6,25),22,(12,12)),edges);self.assertIn((22,(12,14),0,(6,25)),edges)
        self.assertIn(14*16+12,inn['transitionCells']);self.assertIn(5*16+12,inn['dynamicObjectCells'])
        self.assertEqual(4,scene['inns'][0]['price']);self.assertEqual(0x72,scene['inns'][0]['blockedStatusMask'])
    def test_existing_content_rules_and_maps_stay_intact(self):
        before=json.loads(self.base['scene.json']);after=json.loads(self.result['scene.json'])
        for name in ('initialPlayer','initialMoney','items','shops','nanhai'):
            if name in before:self.assertEqual(before[name],after[name],name)
        self.assertTrue({m['id'] for m in before['maps']}.issubset({m['id'] for m in after['maps']}))
        for name in ('combat.json','audio.json'):
            a=json.loads(self.base[name]);b=json.loads(self.result[name]);a.pop('version',None);b.pop('version',None);self.assertEqual(a,b,name)
    def test_bad_price_status_exit_palette_and_rom_are_rejected(self):
        for kind in ('price','status','exit','palette','rom','grid','source'):
            e=json.loads(json.dumps(self.evidence))
            if kind=='price':e['inns'][0]['price']+=1
            elif kind=='status':e['inns'][0]['blockedStatusMask']=0
            elif kind=='exit':e['exits'][1]['trigger']=[12,12]
            elif kind=='palette':e['atlasCorrections'][0]['palette']=[14]*32
            elif kind=='rom':e['ruleRanges'][0]['sha256']='0'*64
            elif kind=='grid':e['maps'][0]['gridSha256']='0'*64
            else:e['atlasCorrections'][0]['previousAssetSha256']='0'*64
            def read(path):return e if Path(path).resolve()==(ci.ROOT/self.proof).resolve() else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin)
    def test_wrong_base_and_target_pin_rejected(self):
        with self.assertRaises(ValueError):exporter.export_from_base(dict(self.base,**{'manifest.json':b'bad'}),self.proof,self.pin)
        with self.assertRaisesRegex(ValueError,'target pin'):exporter.export_from_base(self.base,self.proof,dict(self.pin,manifestSha256='0'*64))

if __name__=='__main__':unittest.main()
