"""Original room28 increment: preserve reviewed assets and reject invented grants."""
import copy
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import ci_apk as ci
import export_development as ex


class TownRoom28ExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin = ex.load(ROOT / 'ci/golden-town-room28-content.json')
        cls.apk = Path(os.environ['FENGSHEN_CONTENT_BASE_APK'])
        cls.base = ci.content(cls.apk, cls.pin['iteration']['base'])
        cls.out = ex.export_from_base(cls.base, cls.pin['iteration']['provenance'], cls.pin)
        parent = ex.load(ROOT / 'ci/golden-world-jiang-content.json')
        previous = os.environ.get('FENGSHEN_PREVIOUS_CONTENT_APK')
        cls.old = ci.content(Path(previous), parent) if previous else ex.export_from_base(
            cls.base, parent['iteration']['provenance'], parent)
        cls.scene = json.loads(cls.out['scene.json'])

    def test_exact_room_only_preserves_existing_media_and_rules(self):
        self.assertEqual(self.pin['manifestSha256'], ex.digest(self.out['manifest.json']))
        self.assertEqual(392, len(self.out))
        self.assertEqual(73, len(self.scene['maps']))
        old = json.loads(self.old['scene.json'])
        self.assertEqual({28}, {m['id'] for m in self.scene['maps']} - {m['id'] for m in old['maps']})
        for name, raw in self.old.items():
            if not name.endswith('.json'):
                self.assertEqual(raw, self.out[name], name)
        for key in ('initialPlayer', 'items', 'shops', 'inns', 'clinics', 'serviceBindings',
                    'ferries', 'freeBoat', 'mapArrivals', 'sceneStories', 'originalJiangJoin'):
            self.assertEqual(old.get(key), self.scene.get(key), key)
        entry = next(e for e in self.scene['exits'] if e['fromMapId'] == 0 and e['toMapId'] == 28)
        back = next(e for e in self.scene['exits'] if e['fromMapId'] == 28)
        self.assertEqual(([12, 23], [6, 10], True),
                         (entry['trigger'], entry['spawn'], entry['captureCaller']))
        self.assertEqual(([6, 10], [12, 23], True),
                         (back['trigger'], back['spawn'], back['returnToCaller']))
        npcs = [n for n in self.scene['npcs'] if n['mapId'] == 28]
        self.assertEqual(2, len(npcs))
        self.assertTrue(npcs[0]['readOnlyDialogue'])
        self.assertTrue(npcs[1]['hiddenInvestigation'])
        self.assertEqual('rom.medicine.0', npcs[1]['treasure']['itemId'])
        self.assertTrue(all(not n['firstEffects'] for n in npcs))

    def test_empty_directory_restore_rebuilds_reviewed_target(self):
        with tempfile.TemporaryDirectory() as td, patch.object(ci, 'CONFIG', self.pin):
            folder = Path(td) / 'content'
            ci.restore(self.apk, folder, next_code=87)
            self.assertEqual(self.out, {p.name: p.read_bytes() for p in folder.iterdir()})

    def test_tampered_target_glyph_grant_and_pose_are_rejected(self):
        import town_room28_resources as resources
        actual = resources.json.loads
        source = ex.load(ROOT / 'game-data/provenance/town-room28-resources.json')
        for mode in ('caller', 'spawn', 'glyph', 'grant', 'effect', 'pose'):
            value = copy.deepcopy(source)
            if mode == 'caller':
                value['bindings'][0]['callerMapId'] = 10
            elif mode == 'spawn':
                value['bindings'][0]['spawn'] = [7, 12]
            elif mode == 'glyph':
                value['rooms'][0]['font']['glyphs'][0]['pixelsSha256'] = '0' * 64
            elif mode == 'grant':
                value['rooms'][0]['npcs'][1]['treasure']['itemId'] = 'rom.medicine.1'
            elif mode == 'effect':
                value['rooms'][0]['npcs'][0]['firstEffects'] = [{'type': 'money', 'amount': 1}]
            else:
                value['rooms'][0]['graphics']['npc-room28-161.png']['tiles'][0]['flipX'] = False
            # Two reads: scoped resource and unchanged original input proof.
            def read_json(text, *args, **kwargs):
                decoded = actual(text, *args, **kwargs)
                return value if isinstance(decoded, dict) and 'rooms' in decoded and \
                    decoded.get('scopeRevision') == resources.REVISION else decoded
            with patch.object(resources.json, 'loads', side_effect=read_json), self.assertRaises(ValueError, msg=mode):
                ex.validate_world_house_resources(ex.iteration_reader(), 28)
        original = ex.load
        recipe_path = ROOT / self.pin['iteration']['provenance']
        recipe = copy.deepcopy(original(recipe_path))
        recipe['exits'][1]['spawn'] = [12, 24]
        with patch.object(ex, 'load', side_effect=lambda p: recipe if Path(p) == recipe_path else original(p)), \
                self.assertRaises(ValueError):
            ex.export_from_base(self.base, self.pin['iteration']['provenance'], self.pin)


if __name__ == '__main__':
    unittest.main()
