"""Scoped original resource and recipe checks, not Android route acceptance."""
import copy, hashlib, io, json, sys, tempfile, unittest
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import export_development as ex
from forensics.fengshen246 import extract_enemy,extract_map,extract_npcs,extract_text,glyph_pixels,decode_tokens,Reader

class JiamengResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=ex.iteration_reader()
        cls.proof=ex.load(ROOT/'game-data/provenance/world-jiameng-resources.json')
        cls.binding=ex.load(ROOT/'game-data/provenance/world-jiameng-binding.json')

    def test_original_grid_stats_context_and_cpu_files_have_actual_sources(self):
        for m in self.proof['maps']:
            raw=extract_map(self.reader,m['mapId']);self.assertEqual(m['gridSha256'],raw['gridSha256'])
            self.assertEqual(m['npcSource'],extract_npcs(self.reader,m['mapId']))
        for e in self.proof['enemies']:
            actual=extract_enemy(self.reader,e['romEnemyId'])
            for k,v in actual.items():self.assertEqual(v,e[k],k)
        for span in self.binding['sources']+self.binding['huangContextCorrection']['sources']:
            ex.checked_span(self.reader,span)
        for row in self.binding['expected']:
            raw=(ROOT/row['path']).read_bytes()
            self.assertEqual(row['sha256'],hashlib.sha256(raw).hexdigest())
            self.assertEqual(row['caseCount'],len(raw.splitlines())-1)
        self.assertEqual(self.binding['probe']['sha256'],hashlib.sha256((ROOT/self.binding['probe']['path']).read_bytes()).hexdigest())
        self.assertEqual(0x7e6,self.reader.word(0,0xd664+2*145))
        self.assertEqual(0x7d0,self.reader.word(0,0xd664+2*121))

    def test_exact_four_original_graphics_reconstruct_through_existing_tool(self):
        self.assertEqual({'enemy158.png','enemy159.png','enemy160.png','enemy161.png'},set(self.proof['graphics']))
        for name,recipe in self.proof['graphics'].items():
            pixels=Image.open(io.BytesIO(ex.scoped_observed_graphic(self.reader,recipe))).convert('RGBA')
            self.assertEqual(recipe['rgbaSha256'],hashlib.sha256(pixels.tobytes()).hexdigest(),name)
            self.assertFalse(recipe['normalPlayEvidence'])
        self.assertTrue(all('paletteCodes' in t for t in self.proof['graphics']['enemy158.png']['tiles']))

    def test_current_dialogues_use_active_font_not_the_opening_charset(self):
        font=self.proof['font'];raw=b''.join(ex.checked_span(self.reader,s)for s in font['sources'])
        cs={int(k):v for k,v in font['charset'].items()}
        for g in font['glyphs']:
            pixels=bytes(n for row in glyph_pixels(raw,0,g['code'])for n in row)
            self.assertEqual(g['pixelsSha256'],hashlib.sha256(pixels).hexdigest())
            self.assertEqual(g['character'],cs[g['code']])
        for t in self.proof['dialogueStreams']:
            actual=extract_text(self.reader,t['group'],t['messageIndex'])
            self.assertEqual(actual['rawHex'],t['rawHex'])
            decoded=decode_tokens(bytes.fromhex(t['rawHex']),cs)
            self.assertEqual([],decoded['unknownCodes']);self.assertEqual(t['text'],decoded['text'])
        self.assertEqual(bytes([255]*4),ex.checked_span(self.reader,font['controlCodes']['68']['source']))
        self.assertEqual('　',cs[68])

    def test_original_completion_boundaries_remain_hash_bound_and_separate_from_app(self):
        proof=ex.load(ROOT/'game-data/provenance/world-jiameng-state.json')
        self.assertEqual(self.binding['romSha256'],proof['romSha256'])
        self.assertEqual('CONTROLLED_ORIGINAL_CPU_NOT_ANDROID_NORMAL_ROUTE',proof['kind'])
        for span in proof['sources']:ex.checked_span(self.reader,span)
        self.assertEqual(proof['probe']['sha256'],hashlib.sha256((ROOT/proof['probe']['path']).read_bytes()).hexdigest())
        self.assertEqual([8,80,25,960],[row['caseCount']for row in proof['expected']])
        for row in proof['expected']:
            raw=(ROOT/row['path']).read_bytes()
            self.assertEqual(row['sha256'],hashlib.sha256(raw).hexdigest())
            self.assertEqual(row['caseCount'],len(raw.splitlines())-1)
        trace=proof['script30'];raw=(ROOT/trace['tracePath']).read_bytes()
        self.assertEqual(trace['traceSha256'],hashlib.sha256(raw).hexdigest())
        self.assertEqual('CONTROLLED_LIVING_PARTY_POST_BATTLE_STAGE_NOT_NORMAL_VICTORY',trace['kind'])
        rows=raw.decode().splitlines()
        texts=[r for r in rows if 'post-text-' in r]
        self.assertEqual([6,7,8],[int(r.split('message=')[1].split('\t')[0])for r in texts])
        self.assertTrue(all('script=30\t' in r and 'yangStatus=64\t' in r for r in texts))
        self.assertEqual({'mapId':37,'x':4,'y':5,'direction':'PRESERVE; no scripted direction write claimed'},trace['destination'])
        self.assertTrue(any('map=37\tphase=0' in r and 'x=253\ty=254' in r and 'event=0\t' in r for r in rows))
        self.assertEqual(3,len(trace['failedMethods']))
        points={tuple(p)for p in proof['rules']['threeBosses']['triggerCells']}
        trigger=next(row for row in proof['expected']if row['caseCount']==960)
        for row in (ROOT/trigger['path']).read_text().splitlines()[1:]:
            x,y,flag,event,phase=map(int,row.split('\t'))
            self.assertEqual((1,11)if flag&128==0 and(x,y)in points else(0,0),(event,phase))
        self.assertEqual('NONE; ordinary battle reward/loot remain separate',proof['rules']['firstBoss']['additionalScriptReward'])
        self.assertEqual(64,proof['rules']['threeBosses']['postBattleBeforeScript30']['yangStatusOr'])

    def test_manual_return_definition_reuses_existing_scoped_export_validation(self):
        definition=ex.world_jiameng_xiao_return_definition()
        ex.validate_world_jiameng_scene_script(self.reader,definition)
        for mode in ('automatic','new-template','wrong-character','partial-recovery','walk-player','reward','map'):
            bad=copy.deepcopy(definition)
            if mode=='automatic':bad['manualActivation']=False
            elif mode=='new-template':bad['continuation']['joinCharacterId']='xiaolongnv'
            elif mode=='wrong-character':bad['continuation']['characterChanges'][0]['characterId']='yangjian'
            elif mode=='partial-recovery':bad['continuation']['characterChanges'][0]['restoreMp']=False
            elif mode=='walk-player':bad['openingMovement']['completedSteps']=1
            elif mode=='reward':bad['continuation']['money']=1
            else:bad['entryTrigger']['mapId']=145
            with self.subTest(mode=mode),self.assertRaises(ValueError):ex.validate_world_jiameng_scene_script(self.reader,bad)

    def test_multi_palette_generator_roundtrip_is_only_a_derived_fixture(self):
        recipe=self.proof['graphics']['enemy158.png']
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'derived.png';p.write_bytes(ex.scoped_observed_graphic(self.reader,recipe))
            regenerated=ex.observed_graphic_recipe(self.reader,p,[0,0,recipe['width'],recipe['height']],True,per_tile_palette=True)
            self.assertEqual(recipe['rgbaSha256'],regenerated['rgbaSha256'])
            self.assertEqual(p.read_bytes(),ex.scoped_observed_graphic(self.reader,regenerated))
            with self.assertRaisesRegex(ValueError,'mixed graphic palette'):
                ex.observed_graphic_recipe(self.reader,p,[0,0,recipe['width'],recipe['height']],True)

    def test_wrong_tile_palette_span_and_overlapping_graphics_rejected(self):
        for kind in ('palette','span','overlap','missing','invalid-palette'):
            r=copy.deepcopy(self.proof['graphics']['enemy158.png'])
            if kind=='palette':r['tiles'][0]['paletteCodes']['0']=[1,2,3]
            elif kind=='span':r['tiles'][0]['sha256']='0'*64
            elif kind=='overlap':r['tiles'][1]['xy']=r['tiles'][0]['xy']
            elif kind=='missing':r['tiles'].pop()
            else:r['tiles'][0]['paletteCodes']['0']=[True,0,0]
            with self.subTest(kind=kind),self.assertRaises(ValueError):ex.scoped_observed_graphic(self.reader,r)

if __name__=='__main__':unittest.main()
