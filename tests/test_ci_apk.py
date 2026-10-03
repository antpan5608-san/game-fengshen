import hashlib
import json
from pathlib import Path
import tempfile
import sys
import subprocess
import unittest
from unittest.mock import patch
import warnings
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools import ci_apk as ci


class ContentTransportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.apk = Path(self.temp.name) / 'fixture.apk'
        self.payload = b'existing exported data'
        self.manifest = json.dumps(dict(schemaVersion=1, version='fixture-c1', files={'scene.json': ci.sha(self.payload)})).encode()
        self.pin = dict(ci.CONFIG, contentVersion='fixture-c1', manifestSha256=ci.sha(self.manifest))
        self.patch = patch.object(ci, 'CONFIG', self.pin)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def write(self, extra=None, payload=None, manifest=None):
        with zipfile.ZipFile(self.apk, 'w') as z:
            z.writestr('assets/development/manifest.json', manifest or self.manifest)
            z.writestr('assets/development/scene.json', self.payload if payload is None else payload)
            for name, value in (extra or []):
                z.writestr('assets/development/' + name, value)

    def test_exact_existing_export(self):
        self.write()
        self.assertEqual(ci.content(self.apk)['scene.json'], self.payload)

    def test_invalid_item_source_preserves_existing_assets_after_valid_hashes(self):
        payload=json.dumps({'items':[{'id':'rom.special.12','source':{'confidence':'ORIGINAL_CONTROLLED_MENU_AND_CPU'}}]}).encode()
        manifest=json.dumps(dict(schemaVersion=1,version='fixture-c1',files={'scene.json':ci.sha(payload)})).encode()
        self.pin['manifestSha256']=ci.sha(manifest)
        self.write(payload=payload,manifest=manifest)
        target=Path(self.temp.name)/'assets';target.mkdir()
        (target/'scene.json').write_bytes(b'previous valid export')
        with patch.object(ci,'verify_apk',return_value={'versionCode':27}):
            with self.assertRaisesRegex(ValueError,'Unsupported item source confidence: rom.special.12'):
                ci.restore(self.apk,target,next_code=28)
        self.assertEqual(b'previous valid export',(target/'scene.json').read_bytes())

    def test_existing_confidence_states_keep_specific_controlled_evidence(self):
        for confidence in ['GAMEPLAY_VERIFIED','PROVISIONAL_REFERENCE']:
            source={'confidence':confidence,'originalEvidenceKind':'CONTROLLED_ORIGINAL_MENU_AND_CPU_NOT_NORMAL_ROUTE'}
            payload={'scene.json':json.dumps({'items':[{'id':'rom.special.12','source':source}]}).encode()}
            ci.validate_item_sources(payload)
            self.assertEqual(source,json.loads(payload['scene.json'])['items'][0]['source'])

    def test_corrupt_file_rejected(self):
        self.write(payload=b'changed')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            ci.content(self.apk)

    def test_unreviewed_manifest_rejected(self):
        self.write(manifest=b'{}')
        with self.assertRaisesRegex(ValueError, 'manifest differs'):
            ci.content(self.apk)

    def test_extra_content_rejected(self):
        self.write(extra=[('unreviewed.json', b'{}')])
        with self.assertRaisesRegex(ValueError, 'file set'):
            ci.content(self.apk)

    def test_hashed_original_map_domain_is_not_capped_at_256_export_files(self):
        # Ordinary original maps already need at least two entries each, before
        # sprites/audio. This checks transport only, not playable map completeness.
        payload={f'map-{i}.json':b'{}' for i in range(350)}
        payload['scene.json']=self.payload
        manifest=json.dumps(dict(schemaVersion=1,version='fixture-c1',files={k:ci.sha(v)for k,v in payload.items()})).encode()
        self.pin['manifestSha256']=ci.sha(manifest)
        with zipfile.ZipFile(self.apk,'w')as z:
            for name,value in payload.items():z.writestr('assets/development/'+name,value)
            z.writestr('assets/development/manifest.json',manifest)
        self.assertEqual(352,len(ci.content(self.apk)))

    def test_archive_entry_safety_bound_remains_enforced(self):
        self.write(extra=[(f'entry-{i}.json',b'')for i in range(ci.MAX_CONTENT_ENTRIES)])
        with self.assertRaisesRegex(ValueError,'excessive'):ci.content(self.apk)

    def test_path_traversal_and_windows_paths_rejected(self):
        for name in ('../escape', '/absolute', 'C:drive', 'nested\\escape', './alias'):
            with self.subTest(name=name):
                with self.assertRaisesRegex(ValueError, 'Unsafe'):
                    ci.validate_content_path(name)

    def test_duplicate_rejected(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')
            self.write(extra=[('scene.json', b'duplicate')])
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            ci.content(self.apk)

    def test_symlink_rejected(self):
        self.write()
        with zipfile.ZipFile(self.apk, 'a') as z:
            entry = zipfile.ZipInfo('assets/development/link')
            entry.external_attr = 0o120777 << 16
            z.writestr(entry, 'scene.json')
        with self.assertRaisesRegex(ValueError, 'symlink'):
            ci.content(self.apk)

    def test_failed_verification_preserves_existing_export(self):
        self.write(payload=b'bad')
        target = Path(self.temp.name) / 'assets'
        target.mkdir()
        (target / 'scene.json').write_bytes(b'previous valid export')
        with patch.object(ci, 'verify_apk', return_value={'versionCode': 21}):
            with self.assertRaises(ValueError):
                ci.restore(self.apk, target, next_code=22)
        self.assertEqual((target / 'scene.json').read_bytes(), b'previous valid export')

    def test_version_must_increase(self):
        self.write()
        with patch.object(ci, 'verify_apk', return_value={'versionCode': 22}):
            with self.assertRaisesRegex(ValueError, 'versionCode'):
                ci.restore(self.apk, Path(self.temp.name) / 'target', next_code=22)

    def test_download_error_redacts_secret_url(self):
        with patch.object(ci.urllib.request, 'urlopen', side_effect=RuntimeError('PRIVATE_TOKEN')):
            with self.assertRaisesRegex(ValueError, 'URL redacted') as error:
                ci.fetch('https://example.test/?PRIVATE_TOKEN', self.apk)
        self.assertNotIn('PRIVATE_TOKEN', str(error.exception))

    def test_wrong_signer_and_debuggable_release_rejected(self):
        badging = "package: name='org.fengshen.dev' versionCode='22' versionName='0.8.2-ci-release'\napplication-debuggable"
        with patch.object(ci, 'tool', side_effect=lambda name: name), patch.object(ci, 'command', return_value='Signer #1 certificate SHA-256 digest: deadbeef'):
            with self.assertRaisesRegex(ValueError, 'signer'):
                ci.verify_apk(self.apk)
        cert = 'Signer #1 certificate SHA-256 digest: ' + ci.CONFIG['signerSha256']
        with patch.object(ci, 'tool', side_effect=lambda name: name), patch.object(ci, 'command', side_effect=[cert, badging]):
            with self.assertRaisesRegex(ValueError, 'debuggable'):
                ci.verify_apk(self.apk, release=True)

    def test_android_tool_output_uses_utf8_not_windows_locale(self):
        output = 'application-label:封神榜'.encode('utf-8')
        with patch.object(ci.subprocess, 'run', return_value=subprocess.CompletedProcess(['aapt'], 0, output, b'')):
            self.assertEqual(ci.command(['aapt']), 'application-label:封神榜')


if __name__ == '__main__':
    unittest.main()
