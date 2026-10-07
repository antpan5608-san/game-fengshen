"""Approved source/package byte verification, never App or visual-quality acceptance."""
import copy
import hashlib
import json
from pathlib import Path
import re
import tempfile
import unittest
import warnings
import zipfile
from PIL import Image
from tools import ci_apk as ci

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'game-data/visual/battle-visual-02'


class VisualAssetTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.apk=Path(self.tmp.name)/'synthetic.apk'
        self.pin=ci.CONFIG['visualPack']
        self.manifest=json.loads((ART/'manifest.json').read_text(encoding='utf-8'))

    def write(self,replace=None,extras=(),omit=()):
        with zipfile.ZipFile(self.apk,'w')as z:
            for p in ART.iterdir():
                if p.name not in omit:z.writestr(self.pin['assetPrefix']+p.name,(replace or {}).get(p.name,p.read_bytes()))
            for name,data in extras:z.writestr(self.pin['assetPrefix']+name,data)

    def test_selected_original_sources_manifest_dimensions_and_no_rejected_candidate(self):
        provenance=json.loads((ROOT/'game-data/provenance/battle-visual-02-assets.json').read_text(encoding='utf-8'))
        self.assertEqual(12,len(provenance['assets']))
        self.assertEqual(self.pin['manifestSha256'],ci.sha((ART/'manifest.json').read_bytes()))
        source=(ROOT/'android/app/src/main/java/org/fengshen/dev/BattleVisualAssets.kt').read_text(encoding='utf-8')
        self.assertIn('MANIFEST_SHA256="'+self.pin['manifestSha256']+'"',source)
        self.assertEqual(set(self.manifest['files'])|{'manifest.json'},{p.name for p in ART.iterdir()})
        self.assertFalse(set(provenance['excluded'])&set(self.manifest['files']))
        for name,item in self.manifest['files'].items():
            p=ART/name;self.assertEqual(item['sha256'],ci.sha(p.read_bytes()));self.assertEqual(item['bytes'],p.stat().st_size)
            with Image.open(p)as im:self.assertEqual((item['width'],item['height']),im.size);im.verify()
        config=json.loads((ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        self.assertEqual('opening-segment-001-c62',config['contentVersion'])
        self.assertEqual('625a314a010f6f41f7cb27af373c750c87399d1dc8b59fba9ea13b1ce2eb8bef',config['manifestSha256'])

    def test_exact_assets_pack_is_verified_separately_from_gameplay_content(self):
        self.write();self.assertEqual(13,ci.visual_content(self.apk,self.pin)['files'])

    def test_corrupt_image_or_manifest_rejected(self):
        for name in ('nezha-idle-v1.png','manifest.json'):
            self.write({name:b'changed'})
            with self.assertRaises(ValueError):ci.visual_content(self.apk,self.pin)

    def test_missing_extra_duplicate_and_symlink_rejected(self):
        self.write(omit=['grass-v1.png'])
        with self.assertRaises(ValueError):ci.visual_content(self.apk,self.pin)
        self.write(extras=[('unexpected.png',b'')])
        with self.assertRaises(ValueError):ci.visual_content(self.apk,self.pin)
        with warnings.catch_warnings():
            warnings.simplefilter('ignore');self.write(extras=[('grass-v1.png',b'')])
        with self.assertRaises(ValueError):ci.visual_content(self.apk,self.pin)
        self.write(omit=['grass-v1.png'])
        with zipfile.ZipFile(self.apk,'a')as z:
            e=zipfile.ZipInfo(self.pin['assetPrefix']+'grass-v1.png');e.external_attr=0o120777<<16;z.writestr(e,'cave-v1.png')
        with self.assertRaises(ValueError):ci.visual_content(self.apk,self.pin)

    def test_wrong_pin_identity_and_prefix_rejected(self):
        self.write()
        for change in (dict(manifestSha256='0'*64),dict(id='unapproved'),dict(assetPrefix='assets/private-inputs/')):
            with self.assertRaises(ValueError):ci.visual_content(self.apk,dict(self.pin,**change))


if __name__=='__main__':unittest.main()
