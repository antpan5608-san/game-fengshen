import unittest,json,hashlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
class AudioPackageTests(unittest.TestCase):
    def test_audio_assets_have_target_recording_chain_and_package_hashes(self):
        package=ROOT/'game-data/packages/development/opening-segment-001-c8'
        if not package.exists():self.skipTest('Local development audio package absent; never download ROM')
        a=json.loads((package/'audio.json').read_text(encoding='utf-8'))
        manifest=json.loads((package/'manifest.json').read_text(encoding='utf-8'))
        self.assertEqual(set(a['maps']),{'114','16','0'})
        for asset in a['assets']:
            src=asset['source'];self.assertEqual(src['confidence'],'HIGH')
            recording=ROOT/src['recording']
            self.assertEqual(hashlib.sha256(recording.read_bytes()).hexdigest(),src['recordingSha256'])
            self.assertEqual(hashlib.sha256((package/asset['file']).read_bytes()).hexdigest(),manifest['files'][asset['file']])
            self.assertGreater(asset['loopEndMs'],asset['loopStartMs'])
        self.assertEqual(a['events']['battle'],a['events']['victory'])
        self.assertEqual(set(a['missingEffects']),{'confirm','cancel','attack','hurt'})
