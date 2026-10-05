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

    def test_same_original_terrain_probe_is_scoped_to_current_grids_and_room(self):
        path='game-data/provenance/world-jiameng-terrain.json';p=ex.load(ROOT/path)
        self.assertEqual(264,p['testCount'])
        for mid in (145,146,147,148):
            rule=ex.validate_world_hall_batch_terrain(self.reader,mid,path)
            self.assertEqual(extract_map(self.reader,mid)['gridSha256'],rule['gridSha256'])
        for mid in (37,76,149):
            with self.assertRaises(ValueError):ex.validate_world_hall_batch_terrain(self.reader,mid,path)
        room=p['postScriptRoom'];self.assertEqual(64,room['testCount']);self.assertEqual([],room['differences'])
        raw=(ROOT/room['cpuExpectedPath']).read_bytes()
        self.assertEqual(room['cpuExpectedSha256'],hashlib.sha256(raw).hexdigest())
        for span in room['sources']:ex.checked_span(self.reader,span)
        for row in raw.decode('ascii').splitlines()[1:]:
            source,target,direction,blocked,plane=map(int,row.split('\t'))
            self.assertIn(source,(0,1,2,5));self.assertIn(target,(0,1,2,5));self.assertIn(direction,(1,2,3,4))
            self.assertEqual((int(target==1),0),(blocked,plane))

    def test_field_actor_recipes_keep_transparent_zero_and_actual_opaque_black(self):
        self.assertEqual({'npc-jiameng-129.png','npc-jiameng-152.png','npc-jiameng-154.png',
            'npc-jiameng-room37-130.png','npc-jiameng-room37-162.png','npc-jiameng-room37-163.png'},set(self.proof['npcGraphics']))
        for name,recipe in self.proof['npcGraphics'].items():
            image=Image.open(io.BytesIO(ex.scoped_observed_graphic(self.reader,recipe))).convert('RGBA')
            self.assertEqual((16,16),image.size);self.assertTrue(recipe['opaquePixelMatch'])
            self.assertEqual(recipe['opaquePixelCount'],sum(p[3]>0 for p in image.getdata()),name)
            self.assertTrue(any(p[:3]==(0,0,0)and p[3]==255 for p in image.getdata()))
            self.assertTrue(any(p[3]==0 for p in image.getdata()))
            self.assertFalse(recipe['normalPlayEvidence'])
            self.assertTrue(all('attribute' in t for t in recipe['tiles']))

    def test_oam_recipe_helper_preserves_black_flips_and_rejects_wrong_actor_stride(self):
        # Synthetic layout from original tile bytes, not an App/normal screenshot.
        recipe=self.proof['npcGraphics']['npc-jiameng-room37-130.png']
        pixels=Image.open(io.BytesIO(ex.scoped_observed_graphic(self.reader,recipe))).convert('RGBA')
        ram=bytearray(0x800);ppu=bytearray(0x4000);ram[0x418]=130
        for i,tile in enumerate(recipe['tiles']):
            dx,dy=tile['xy'];index=i+1;attribute=tile['attribute']
            ram[0x214+4*i:0x218+4*i]=bytes([40+dy+7,index,attribute,32+dx])
            ppu[0x1000+index*16:0x1000+(index+1)*16]=self.reader.data[tile['offset']:tile['offset']+16]
        with tempfile.TemporaryDirectory()as td:
            root=Path(td);(root/'ram.bin').write_bytes(ram);(root/'ppu.bin').write_bytes(ppu)
            capture=Image.new('RGB',(64,64),(8,16,24));capture.paste(pixels,(32,40),pixels);capture.save(root/'fixture.png')
            result=ex.observed_oam_graphic_recipe(self.reader,root/'fixture.png',root/'ram.bin',root/'ppu.bin',0x214,record_offset=0x418,entity_id=130)
            self.assertEqual(recipe['rgbaSha256'],result['rgbaSha256'])
            self.assertEqual(recipe['opaquePixelCount'],result['opaquePixelCount'])
            for offset in (0x416,0x430,0x800):
                with self.assertRaises(ValueError):
                    ex.observed_oam_graphic_recipe(self.reader,root/'fixture.png',root/'ram.bin',root/'ppu.bin',0x214,record_offset=offset,entity_id=130)
            with self.assertRaises(ValueError):
                ex.observed_oam_graphic_recipe(self.reader,root/'fixture.png',root/'ram.bin',root/'ppu.bin',0x214,record_offset=0x418,entity_id=163)

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

    def test_post_script_room_uses_its_actual_font_and_context_not_cave_text(self):
        room=self.proof['postScriptRoom'];font=room['font']
        self.assertEqual([38,39],font['chr2kBanks'])
        raw=b''.join(ex.checked_span(self.reader,s)for s in font['sources'])
        cs={int(k):v for k,v in font['charset'].items()}
        for glyph in font['glyphs']:
            self.assertEqual(glyph['character'],cs[glyph['code']])
            self.assertEqual(glyph['pixelsSha256'],hashlib.sha256(bytes(n for row in glyph_pixels(raw,0,glyph['code'])for n in row)).hexdigest())
        for stream in room['dialogueStreams']:
            actual=extract_text(self.reader,47,stream['messageIndex'])
            self.assertEqual(actual['rawHex'],stream['rawHex']);self.assertEqual(actual['range'],stream['source']['record'])
            decoded=decode_tokens(bytes.fromhex(stream['rawHex']),cs)
            self.assertEqual([],decoded['unknownCodes']);self.assertEqual(stream['text'],decoded['text'])
        self.assertEqual(196,room['contextId']);self.assertEqual(extract_npcs(self.reader,196),room['contextNpcSource'])
        self.assertFalse(font['normalPlayEvidence']);self.assertEqual('罩',room['differences'][0]['historicalLabel'])
        self.assertEqual('照',room['differences'][0]['currentScopedTranscription'])

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
        self.assertEqual(0x7d6,self.reader.word(0,0xd664+2*37))
        self.assertEqual(0x7d0,self.reader.word(0,0xd664+2*101))
        self.assertEqual(196,proof['rules']['threeBosses']['postBattleBeforeScript30']['map37Context'])
        self.assertNotIn('map101Context',proof['rules']['threeBosses']['postBattleBeforeScript30'])

    def test_boss_definitions_reject_extra_rewards_wrong_groups_and_context(self):
        first,three=ex.world_jiameng_boss_definitions(self.reader)
        for definition in (first,three):ex.validate_world_jiameng_boss(self.reader,definition)
        for mode in ('context','source','slot','reward','trigger','departure','destination','extra-dialogue'):
            bad=copy.deepcopy(first if mode in ('context','source','slot','reward')else three)
            if mode=='context':bad['activationFlagId']='rom.npccontext.121.215'
            elif mode=='source':bad['sourceType']=171
            elif mode=='slot':bad['group']['entities'][0]['slot']=0
            elif mode=='reward':bad['moneyReward']=100
            elif mode=='trigger':bad['additionalEntryTriggers'].pop()
            elif mode=='departure':bad['victoryCharacterChanges'][0]['statusOrMask']=0
            elif mode=='destination':bad['continuation']['destination']['mapId']=101
            else:bad['continuation']['dialogueIds'].append('rom.dialogue.148.9')
            with self.subTest(mode=mode),self.assertRaises(ValueError):ex.validate_world_jiameng_boss(self.reader,bad)

    def test_actual_xiao_restore_timing_is_dialogue_closure_not_selection(self):
        proof=ex.load(ROOT/'game-data/provenance/world-jiameng-state.json');timing=proof['xiaoReturnTiming']
        raw=(ROOT/timing['tracePath']).read_bytes();self.assertEqual(timing['traceSha256'],hashlib.sha256(raw).hexdigest())
        rows=[line.split('\t')for line in raw.decode('ascii').splitlines()[1:]];self.assertEqual(8,len(rows))
        for row in rows[:3]:self.assertEqual([32,0,92,44,44,0,0],list(map(int,row[3:])))
        self.assertEqual([0,2,0,92,92,44,44,4,216],list(map(int,rows[3][1:])))
        self.assertIn('After first dialogue closes',timing['effectTiming'])

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

    def test_zone29_keeps_all_original_groups_and_exact_graphic_pixels(self):
        from forensics.fengshen246 import extract_encounter_groups
        zone=self.proof['zone29'];self.assertEqual(extract_encounter_groups(self.reader,29),zone['groups'])
        self.assertEqual(12,len(zone['groups']['groups']))
        self.assertEqual({60,61,62},{m['enemyId']for g in zone['groups']['groups']for m in g['entities']})
        for n,recipe in zone['graphics'].items():
            self.assertFalse(recipe['normalPlayEvidence'])
            self.assertEqual(recipe['originalEnemyId'],self.reader.read(1,0x9ea3+recipe['sourceType'])[0])
            image=Image.open(io.BytesIO(ex.scoped_observed_graphic(self.reader,recipe))).convert('RGBA')
            self.assertEqual(recipe['rgbaSha256'],ex.digest(image.tobytes()),n)
        # Derived output checks exporter colour collapse; not a new gameplay capture.
        recipe=zone['graphics']['enemy62.png']
        with tempfile.TemporaryDirectory()as td:
            f=Path(td)/'derived.png';f.write_bytes(ex.scoped_observed_graphic(self.reader,recipe))
            regenerated=ex.observed_graphic_recipe(self.reader,f,[0,0,72,72],False,
                per_tile_palette=True,allow_collapsed_palette=True)
            self.assertEqual(recipe['rgbaSha256'],regenerated['rgbaSha256'])
            with self.assertRaisesRegex(ValueError,'not matched'):
                ex.observed_graphic_recipe(self.reader,f,[0,0,72,72],False,per_tile_palette=True)

    def test_zone29_state08_reuses_original_priority_without_damage_or_other_target_changes(self):
        enemy=dict(id=60,behaviorByte=8,behaviorEvidence='game-data/provenance/world-jiameng-resources.json')
        ex.validate_world_jiameng_status8(self.reader,enemy)
        for fields in ({'id':29},{'behaviorByte':9},{'requiredBindingMarker':5},{'specialBaseDamage':10}):
            with self.subTest(fields=fields),self.assertRaises(ValueError):
                ex.validate_world_jiameng_status8(self.reader,enemy|fields)

    def test_scoped_special18_capability_keeps_parent_and_rejects_other_protection(self):
        scene=json.loads((ROOT/'android/app/src/main/assets/development/scene.json').read_text(encoding='utf-8'))
        item=next(i for i in scene['items']if i['id']=='rom.special.18');before=copy.deepcopy(item)
        update=ex.world_jiameng_item_update(item);self.assertEqual(before,item)
        self.assertEqual({'battleBindingUse'},set(update['fields']))
        self.assertEqual(5,update['fields']['battleBindingUse']['bindingMarker'])
        for n in (158,159,160,161):ex.validate_world_jiameng_binding(self.reader,dict(id=n,
            requiredBindingMarker=5,bindingEvidence='game-data/provenance/world-jiameng-binding.json'))
        for n,marker in ((157,5),(60,5),(158,2),(161,1)):
            with self.subTest(n=n,marker=marker),self.assertRaises(ValueError):
                ex.validate_world_jiameng_binding(self.reader,dict(id=n,requiredBindingMarker=marker,
                    bindingEvidence='game-data/provenance/world-jiameng-binding.json'))
        for fields in ({'maxCount':10},{'category':'medicine'},{'battleBindingUse':{}},{'originalId':13}):
            with self.subTest(fields=fields),self.assertRaises(ValueError):ex.world_jiameng_item_update(item|fields)

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
