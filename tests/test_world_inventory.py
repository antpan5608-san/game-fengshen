"""Table evidence must not promote decoding, aliases, or NPC overlays into completion."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from export_development import iteration_reader
from forensics.fengshen246 import extract_world_inventory,extract_default_map_palette,Reader

class WorldInventoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=iteration_reader();cls.report=extract_world_inventory(cls.reader,{0,16,17,18,19,25,97,114})
    def test_physical_geometry_and_npc_domains_stay_separate(self):
        r=self.report
        self.assertEqual(list(range(175)),[m['mapId'] for m in r['maps']])
        self.assertEqual(233,r['npcContextCount'])
        self.assertEqual('NPC_OVERLAY_ONLY',r['npcContexts'][176]['kind'])
        self.assertEqual(175,r['unresolved'][0]['index']);self.assertIsNone(r['effectiveMapCount'])
        self.assertFalse(r['referenceIsDenominator']);self.assertEqual('NO',r['summary']['allMapsUsable'])
    def test_shared_interiors_keep_each_real_service_context(self):
        services={s['id']:s for s in self.report['services']}
        for village in range(16):
            inn=services[f'rom.service.{village}.inn'];self.assertEqual(22,inn['interiorMapId'])
            self.assertEqual(village,inn['callerMapId']);self.assertEqual(1,len(inn['entryCells']))
        self.assertNotIn('rom.service.8.weapon',services);self.assertNotIn('rom.service.9.armor',services)
        self.assertEqual([[6,25]],services['rom.service.0.inn']['entryCells'])
        self.assertIn('rom.service.map116.npc2',services) # Services outside the sixteen town doors remain visible.
    def test_npc_coordinates_keep_encoding_and_overlay_appearance_separate(self):
        contexts={c['contextId']:c for c in self.report['npcContexts']}
        self.assertEqual([7,5],contexts[17]['records'][0]['cell'])
        self.assertEqual([12,5],contexts[22]['records'][0]['cell'])
        services={s['id']:s for s in self.report['services']}
        extra=services['rom.service.0.weapon']['additionalNpcCandidates']
        self.assertEqual([10,9],extra[0]['cell']);self.assertTrue(extra[0]['positionInRoom'])
        # Context 23 is NOT an empty sentinel: its real records lie outside the shop.
        self.assertEqual(5,len(contexts[23]['records']))
        self.assertTrue(all(not n['positionInRoom'] for n in services['rom.service.0.armor']['additionalNpcCandidates']))
        self.assertEqual('NEEDS_NPC_STATE_DISPATCH',extra[0]['appearance'])
    def test_identical_grids_do_not_merge_ids_or_events(self):
        rows={m['mapId']:m for m in self.report['maps']}
        self.assertEqual(rows[69]['gridSha256'],rows[158]['gridSha256'])
        self.assertNotEqual(rows[69]['mapId'],rows[158]['mapId'])
        self.assertEqual((256,181),(rows[16]['width'],rows[16]['height']))
    def test_packaging_and_runtime_are_independent_of_decode(self):
        rows=self.report['maps'];self.assertEqual(8,sum(m['packaged'] for m in rows))
        self.assertTrue(all(m['decode']=='PASS' for m in rows))
        self.assertTrue(all(m['appRender']=='NOT_RUN' for m in rows))
        self.assertEqual(527,self.report['summary']['exitRecords'])
        self.assertEqual('RETURN_TO_CALLER',next(m for m in rows if m['mapId']==22)['exits'][0]['kind'])
    def test_category_local_stock_and_prices_are_not_reference_ids(self):
        catalog=self.report['serviceCatalog'];items={x['id']:x for x in catalog['items']}
        self.assertEqual(54,len(catalog['stocks']))
        self.assertEqual(15,items['rom.medicine.0']['buyPrice'])
        self.assertEqual(20,items['rom.medicine.6']['buyPrice'])
        self.assertEqual(7,items['rom.medicine.0']['sellPrice'])
        self.assertEqual(['rightHand'],items['rom.item.0']['nezhaPermittedSlots'])
        self.assertEqual(['feet'],items['rom.armor.28']['nezhaPermittedSlots'])
        self.assertTrue(items['rom.weapon.9']['nezhaPermittedByCategoryList'])
        self.assertEqual('NEEDS_SLOT_FILTER_DISPATCH',items['rom.weapon.9']['slotStatus'])
        self.assertEqual([],items['rom.weapon.9']['nezhaPermittedSlots'])
        self.assertEqual([4,8,20,40],catalog['innPrices'][:4])
        rows={s['id']:s for s in self.report['services']}
        self.assertEqual([0,6],rows['rom.service.0.medicine']['stock']['originalIds'])
        self.assertEqual(550,rows['rom.service.15.inn']['price'])
    def test_changed_rom_is_not_an_inventory_source(self):
        wrong=bytearray(self.reader.data);wrong[32]^=1
        with self.assertRaisesRegex(ValueError,'fingerprint'):extract_world_inventory(Reader(bytes(wrong),verify=False))
    def test_rom_palette_matches_completed_normal_armor_and_inn_capture(self):
        # Reviewed normal capture palette, not an emulator fixture or a guessed room tint.
        observed=[14,7,23,55,25,7,38,22,25,33,23,22,25,0,16,48,14,14,54,22,25,14,54,43,25,14,38,34,25,14,38,32]
        for mid in (18,22):self.assertEqual(observed,extract_default_map_palette(self.reader,mid)['palette'])
        for row in self.report['maps']:
            p=row['defaultPalette'];self.assertEqual(32,len(p['palette']));self.assertTrue(p['remainingUnknown'])

if __name__=='__main__':unittest.main()
