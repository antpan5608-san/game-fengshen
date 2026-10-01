"""Local development adapter checks; preserve the original 73-test research baseline."""
import unittest
import sys
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.common import ROOT,load
from forensics.fengshen246 import digest,SHA256

class DevelopmentPackageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=ROOT/'game-data/packages/development/map114-a1'
        if not (cls.root/'manifest.json').exists():raise unittest.SkipTest('Local development package absent; no ROM download')
        cls.data=load(cls.root/'scene.json')
    def test_grid_is_existing_rom_extraction_not_screenshot(self):
        m=load(ROOT/'game-data/raw/rom/maps/114.json');d=self.data
        self.assertEqual(d['grid'],[t for row in m['grid'] for t in row]);self.assertEqual(d['source']['romSha256'],SHA256)
        self.assertEqual((d['width'],d['height'],d['logicalWidth'],d['logicalHeight']),(32,30,256,240))
    def test_enabled_cells_observed_connected_floor_only(self):
        d=self.data;enabled=set(d['enabledCells']);self.assertEqual(len(enabled),34)
        self.assertTrue(all(d['collision'][i]==0 for i in enabled))
        observed=set()
        for probe in ('slice-probe','world-probe'):
            for line in (ROOT/f'private-derived/{probe}/trajectory.tsv').read_text().splitlines():
                a=list(map(int,line.split('\t')))
                if a[1]==114:observed.add(((a[3]-120)//16)*32+(a[2]-120)//16)
        self.assertTrue(enabled<=observed)
        seen={21*32+8};queue=list(seen)
        while queue:
            i=queue.pop()
            for n in (i-1,i+1,i-32,i+32):
                if n in enabled and n not in seen:seen.add(n);queue.append(n)
        self.assertEqual(seen,enabled)
    def test_assets_manifest_hashes_and_sprite_rom_chain(self):
        m=load(self.root/'manifest.json')
        for name,expected in m['files'].items():self.assertEqual(digest((self.root/name).read_bytes()),expected)
        # Existing A output, before switching to the shared CHR decoder.
        # These goldens detect changes in color, transparency and OAM flipping.
        golden={
            'tiles.png':'de50358e33ea81d43d0b5ee834a22240234e384ab62c76ed37dc223dda3e42dd',
            'player-down.png':'cde167be0499a8cf951b1220200e08600d532bf6ec8a9be028905aab578db85f',
            'player-left.png':'f89a1b25b0ba376684b76dca2b7fb6ed1c6249638381ec3954c4fbd678dd1b23',
            'player-right.png':'61935acaddb2e25a0ab32d4a39d3eb0ed6779e74175da536d52e571a8b373790',
            'player-up.png':'0d3f4cdeddb16cb27bea3e0c9c9c3ea469d49d71a2b2fb874fd7b4aecb496ead',
        }
        for name,expected in golden.items():self.assertEqual(digest((self.root/name).read_bytes()),expected)
        with Image.open(self.root/'tiles.png') as im:self.assertEqual(im.size,(256,256))
        for pose in self.data['source']['spritePoses']:
            with Image.open(self.root/f'player-{pose["direction"]}.png') as im:self.assertEqual(im.size,(16,16));self.assertEqual(im.mode,'RGBA')
            self.assertGreaterEqual(pose['romOffset'],0x80010)
    def test_development_does_not_unlock_canonical(self):
        c=load(ROOT/'game-data/canonical/baseline.json');self.assertFalse(c['runtimeReady']);self.assertFalse(any(c['entities'].values()))
        self.assertEqual(self.data['channel'],'development');self.assertTrue(self.data['limitations'])

class OpeningRoutePackageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=ROOT/'game-data/packages/development/opening-to-world-b1'
        if not (cls.root/'manifest.json').exists():raise unittest.SkipTest('Local opening route package absent')
        cls.opening=load(cls.root/'scene.json');cls.world=load(cls.root/'scene16.json')
    def test_both_maps_and_exit_match_original_evidence(self):
        a=load(ROOT/'game-data/raw/rom/maps/114.json');b=load(ROOT/'game-data/raw/rom/maps/16.json')
        transition=load(ROOT/'game-data/raw/rom/transitions-v1.json')
        self.assertEqual(self.opening['grid'],[t for row in a['grid'] for t in row])
        self.assertEqual(self.world['grid'],[t for row in b['grid'] for t in row])
        self.assertEqual((self.opening['spawn'],self.opening['exits'][0]['trigger']),([8,21],[8,29]))
        self.assertEqual((self.opening['exits'][0]['toMapId'],self.opening['exits'][0]['spawn']),
            (transition['toMap'],transition['destinationPlayerMetatile']))
        self.assertEqual(self.opening['exits'][0]['rawHex'],transition['rawHex'])
    def test_opening_route_uses_collision_not_old_observation_mask(self):
        d=self.opening;self.assertEqual(len(d['enabledCells']),443)
        self.assertEqual(set(d['walkableClasses']),{0,2})
        self.assertTrue(all(d['collision'][i] in (0,2) for i in d['enabledCells']))
        self.assertTrue(all(y*d['width']+8 in d['enabledCells'] for y in range(21,30)))
        self.assertEqual([d['collision'][y*d['width']+8] for y in (26,27)],[2,2])
        self.assertNotIn(0,self.world['enabledCells'])
        self.assertIn(142*256+203,self.world['enabledCells'])
    def test_package_hashes_and_new_world_atlas(self):
        files=load(self.root/'manifest.json')['files']
        for name,expected in files.items():self.assertEqual(digest((self.root/name).read_bytes()),expected)
        with Image.open(self.root/'tiles16.png') as im:self.assertEqual(im.size,(256,256))
        self.assertEqual(self.world['source']['romSha256'],SHA256)

class OpeningSegmentPackageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=ROOT/'game-data/packages/development/opening-segment-001-c4'
        if not (cls.root/'manifest.json').exists():raise unittest.SkipTest('Local Segment 001 package absent')
        cls.data=load(cls.root/'scene.json')
    def test_npc_dialogue_and_gift_sources(self):
        d=self.data;raw=load(ROOT/'game-data/raw/rom/npc-opening.json')['records']
        dialogues={x['id']:x for x in d['dialogues']}
        self.assertEqual(len(d['npcs']),8);self.assertEqual(len(dialogues),12)
        for n in d['npcs']:
            r=raw[n['originalIndex']]
            self.assertEqual(n['cell'],r['metatile'])
            self.assertEqual(n['source']['romOffset'],r['range']['offset'])
            self.assertIn(n['firstDialogue'],dialogues)
        self.assertEqual(dialogues['rom.dialogue.124.10']['text'],
            next(x['text'] for x in load(ROOT/'game-data/raw/rom/dialogues-opening.json')['records'] if x['messageIndex']==10))
        self.assertEqual(d['npcs'][1]['firstEffects'][0]['confidence'],'PROVISIONAL_REFERENCE')
        self.assertFalse(d['npcs'][1]['firstEffects'][0]['originalVerified'])
        self.assertEqual(d['npcs'][1]['firstEffects'][0]['amount'],100)
        self.assertEqual(d['npcs'][2]['firstEffects'][0]['id'],'rom.item.0')
        self.assertEqual(d['npcs'][2]['firstEffects'][0]['confidence'],'GAMEPLAY_VERIFIED')
        self.assertFalse(d['intro']['source']['originalVerified'])
    def test_npc_oam_pose_and_manifest_golden(self):
        files=load(self.root/'manifest.json')['files']
        for name,expected in files.items():self.assertEqual(digest((self.root/name).read_bytes()),expected)
        self.assertEqual(files['npc-0.png'],'39d3320ce894dbd581ef0bedd47c59a9be170bcfef8b3c6a0cfaf497e8adf34e')
        self.assertEqual(files['npc-1.png'],'b3f7617beb9a17cc819acf2a413d73fd830430c7699d377b25f3fc43659e1dc9')
        self.assertTrue(all(ref['ramSha256']==digest((ROOT/'private-derived/npc-probe/frame-0450-ram.bin').read_bytes())
            for ref in self.data['source']['npcSpritePoses']))
        rom=next((ROOT/'reference/rom').rglob('*.nes')).read_bytes()
        for bank in self.data['source']['npcChrBanks']:
            self.assertEqual(digest(rom[bank['romOffset']:bank['romOffset']+bank['length']]),bank['romRangeSha256'])
    def test_portrait_reuses_rom_traced_player_sprite(self):
        hero=self.data['initialPlayer'];source=hero['portraitSource']
        self.assertEqual(hero['name'],'哪吒')
        self.assertEqual(hero['portraitAsset'],'player-down.png')
        self.assertEqual(source['kind'],'ROM_OAM_POSE')
        self.assertEqual(source['romRangeSha256'],self.data['source']['spritePoses'][0]['romRangeSha256'])
        self.assertEqual(digest((self.root/hero['portraitAsset']).read_bytes()),
            load(self.root/'manifest.json')['files'][hero['portraitAsset']])
    def test_opening_knife_and_max_mp_match_normal_input_ram(self):
        evidence=load(ROOT/'game-data/provenance/equipment-opening.json')
        self.assertEqual(evidence['romSha256'],SHA256)
        for name,sha in evidence['captures'].items():
            self.assertEqual(digest((ROOT/'private-derived/equipment-probe-final'/name).read_bytes()),sha)
        hero=self.data['initialPlayer'];item=self.data['items'][0]
        self.assertEqual((hero['mp'],hero['maxMp']),(0,0))
        self.assertEqual(hero['equipment'],{'rightHand':0,'leftHand':-1,'body':0,'feet':28})
        self.assertEqual((item['id'],item['name'],item['equipment']['attackBonus']),('rom.item.0','小刀',2))
        self.assertTrue(item['source']['originalVerified'])
