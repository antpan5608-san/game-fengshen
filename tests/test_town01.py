"""TOWN-01 scoped ROM and exported content contracts; no ROM download."""
import unittest,sys
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.common import ROOT,load
from forensics.fengshen246 import Reader,extract_town_shops,digest,SHA256

class Town01Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        paths=[p for p in (ROOT/'reference/rom').glob('*.nes') if digest(p.read_bytes())==SHA256]
        if not paths:raise unittest.SkipTest('Pinned local ROM absent; never download')
        cls.r=Reader(paths[0].read_bytes());cls.town=extract_town_shops(cls.r)
        cls.root=ROOT/'game-data/packages/development/opening-segment-001-c11'
        cls.data=load(cls.root/'scene.json')
    def test_original_stock_and_prices(self):
        self.assertEqual([[r['price']for r in s['rows']]for s in self.town['shops']],[[15,50,120],[30,80,200],[15,20]])
        for shop in self.town['shops']:
            p=shop['stockRange'];self.assertEqual(digest(self.r.data[p['offset']:p['offset']+p['length']]),p['sha256'])
    def test_original_doors_and_scoped_return_rows(self):
        for s in self.town['shops']:
            entry=next(e for e in self.data['exits'] if e['fromMapId']==0 and e['toMapId']==s['mapId'])
            back=next(e for e in self.data['exits'] if e['fromMapId']==s['mapId'])
            self.assertEqual(entry['trigger'],s['door']);self.assertEqual(back['spawn'],s['door'])
            p=back['originalRow'];raw=self.r.data[p['offset']:p['offset']+5]
            self.assertEqual(list(raw[:2]),s['spawn']);self.assertEqual(raw[2],254)
    def test_town_edges_match_original_dispatch_calls(self):
        meanings={0xd214:{'UP'},0xd21d:{'DOWN'},0xd226:{'LEFT'},0xd22f:{'RIGHT'},
            0xd238:{'UP','RIGHT'},0xd248:{'UP','LEFT'},0xd269:{'DOWN','RIGHT'},0xd278:{'DOWN','LEFT'}}
        for table,key in [(0xcb7d,'sourceEdges'),(0xcee4,'targetEdges')]:
            for c,expected in self.town[key].items():
                handler=self.r.word(0,table+2*c)+1
                self.assertEqual(self.r.read(0,handler)[0],0x20)
                self.assertEqual(set(expected),meanings[self.r.word(0,handler+1)])
    def test_six_maps_and_merchant_counter_positions(self):
        self.assertEqual({m['id']for m in self.data['maps']},{114,16,0,17,18,19})
        # Transcribed from the pinned ROM's transaction screenshots, not Reference:
        # weapon-trade/006218, armor-trade/006191, items-edges/006367.
        self.assertEqual([s['buyPrompt'] for s in self.data['shops']],
                         ['你想買些什麼？','好，公子要買什麼嗎？','要買寶貝嗎？'])
        for item in self.data['items']:
            if item['category']=='medicine':
                self.assertNotIn('preview',item['source']['verifiedFields'])
        for n in self.data['npcs']:
            if n['mapId'] not in (17,18,19):continue
            self.assertEqual(n['interactionCell'],[n['cell'][0],n['cell'][1]+2])
            room=load(self.root/f"scene{n['mapId']}.json")
            self.assertEqual((room['width'],room['height']),(16,15))
            self.assertEqual(room['collision'][(n['cell'][1]+1)*16+n['cell'][0]],1)
    def test_equipment_rom_composition_and_knife_shape(self):
        knife=next(i for i in self.data['items']if i['id']=='rom.item.0')
        preview=knife['preview'];s=preview['source'];raw=self.r.data[s['offset']:s['offset']+s['length']]
        self.assertEqual(raw.hex(),'02020010112021')
        for i in self.data['items']:
            if 'preview' not in i:continue
            p=i['preview'];self.assertEqual(digest(self.r.data[p['chrOffset']:p['chrOffset']+p['chrLength']]),p['chrSha256'])
        image=Image.open(self.root/preview['asset']);native=Image.open(ROOT/'private-derived/town01-weapon-trade/006218-transaction.png').convert('RGB').crop((128,41,144,57))
        self.assertEqual([v>0 for v in image.getchannel('A').getdata()],[v!=(0,0,0)for v in native.getdata()])
    def test_scoped_equipment_stats_and_sell_formula(self):
        self.assertEqual([i['equipment']['attackBonus']for i in self.data['items']if i['category']=='weapon'],[2,5,10])
        self.assertEqual([i['equipment']['defenseBonus']for i in self.data['items']if i['category']=='armor'],[2,6,0])
        for i in self.data['items']:self.assertEqual(i['sellPrice'],max(1,i['buyPrice']//2))
        self.assertIn(bytes.fromhex('a9028507208bee'),self.r.read(2,0xbeb8,25))
    def test_original_evidence_and_no_canonical_unlock(self):
        evidence=load(ROOT/'game-data/provenance/town01.json');self.assertEqual(evidence['romSha256'],SHA256)
        for path,sha in evidence['captures'].items():self.assertEqual(digest((ROOT/path).read_bytes()),sha)
        for name,sha in load(self.root/'manifest.json')['files'].items():self.assertEqual(digest((self.root/name).read_bytes()),sha)
        self.assertFalse(load(ROOT/'game-data/canonical/baseline.json')['runtimeReady'])

if __name__=='__main__':unittest.main()
