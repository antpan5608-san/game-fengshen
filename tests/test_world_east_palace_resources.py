"""Original data batch checks; full export/App acceptance belong to the integrator."""
import copy
import json
import sys
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from export_development import iteration_reader,checked_span,scoped_observed_graphic
from forensics.fengshen246 import (digest,extract_map,extract_npcs,extract_default_map_palette,
    extract_encounter_groups,extract_enemy,extract_growth_candidates)


class EastPalaceResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reader=iteration_reader()
        read=lambda name:json.loads((ROOT/'game-data/provenance'/name).read_text(encoding='utf-8'))
        cls.base=read('world-cave85-content.json')
        cls.batch=read('world-east-palace-content.json')
        cls.script=read('world-east-palace-script.json')
        cls.party=read('world-party-xiaolongnv.json')

    def test_cumulative_batch_keeps_all_cave_input_and_original_assets(self):
        for field in ['maps','exits','npcs','dialogues','items','ruleRanges','limitations']:
            self.assertEqual(self.base[field],self.batch[field][:len(self.base[field])],field)
        for field in ['enemies','zones','graphics','bosses','horizons','weaponHits']:
            old=self.base['combatOverlay'][field]
            self.assertEqual(old,self.batch['combatOverlay'][field][:len(old)],field)
        for asset,recipe in self.base['graphics'].items():
            self.assertEqual(recipe,self.batch['graphics'][asset],asset)
        self.assertEqual(self.base['baseManifestSha256'],self.batch['baseManifestSha256'])
        self.assertEqual(self.base['shops'],self.batch['shops'])
        self.assertEqual(self.base['itemUpdates'],self.batch['itemUpdates'])

    def test_all_rom_spans_are_fixed_and_new_map_geometry_is_complete(self):
        r=self.reader
        def inspect(value):
            if isinstance(value,dict):
                if {'offset','length','sha256'}<=value.keys():checked_span(r,value)
                for child in value.values():inspect(child)
            elif isinstance(value,list):
                for child in value:inspect(child)
        inspect(self.batch);inspect(self.script)
        for mid in [95,23]:
            recipe=next(m for m in self.batch['maps']if m['mapId']==mid)
            original=extract_map(r,mid)
            self.assertEqual(original['gridSha256'],recipe['gridSha256'])
            self.assertEqual(extract_default_map_palette(r,mid)['palette'],recipe['palette'])
            self.assertEqual(original['tilesetId'],recipe['terrain']['tileset'])
            self.assertEqual(sorted(recipe['npcCells']),sorted(
                ((n['yCandidate']-120)//16)*original['width']+(n['xCandidate']-120)//16
                for n in extract_npcs(r,mid)['records']))
        self.assertEqual({95,23},{m['mapId']for m in self.batch['maps']}-{m['mapId']for m in self.base['maps']})

    def test_real_directed_exits_preserve_direction_and_do_not_invent_north_link(self):
        new=self.batch['exits'][len(self.base['exits']):]
        self.assertEqual({(25,(49,21),95,(13,29)),(95,(13,29),25,(49,21))},
            {(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in new})
        for edge in new:
            self.assertEqual(bytes(edge['trigger']+[edge['toMapId']]+edge['spawn']),checked_span(self.reader,edge['source']))
            self.assertTrue(edge['preserveArrivalDirection'])
            self.assertTrue(edge['resetEncounterSteps'])
            self.assertEqual(self.reader.read(0,0xaa1f,0x29),checked_span(self.reader,edge['arrivalDirectionSource']))
        proof=self.script['directionEvidence']
        self.assertEqual({'UP':0,'DOWN':1,'LEFT':2,'RIGHT':3},proof['mapping'])
        self.assertEqual([0,1,2,3],[case['after']for case in proof['loaderCases']])

    def test_complete_groups_and_original_hell_rectangles_are_not_replaced_by_default(self):
        r=self.reader
        for zone,mid,count in [(7,95,12),(8,23,13)]:
            data=next(z for z in self.batch['combatOverlay']['zones']if z['mapId']==mid)
            self.assertEqual(zone,data['id']);self.assertEqual(count,len(data['groups']))
            self.assertEqual(extract_encounter_groups(r,zone)['groups'],data['groups'])
            self.assertEqual((245,'HIGH')if mid==95 else(16,'LOW'),(data['randomThreshold'],data['randomGate']))
        hell=next(z for z in self.batch['combatOverlay']['zones']if z['mapId']==23)
        self.assertEqual([[1,72,56,95]],hell['rectangles'])
        self.assertEqual(bytes([8,1,72,56,95,0]),checked_span(r,hell['rectangleSource']))
        self.assertFalse(any(z['mapId']==23 for z in self.batch['combatOverlay']['fallbackZones']))
        scene=next(m for m in self.batch['maps']if m['mapId']==23)
        expected=[]
        for pointer in [0xc240,0xc236]:
            self.assertIn(r.read(11,pointer)[0],[10,13])
            expected.extend([list(r.read(11,pointer+1,4)),list(r.read(11,pointer+5,4))])
        self.assertEqual(expected,scene['unavailableRegions'])
        self.assertEqual(expected,self.script['zone8']['unavailableRegions'])
        self.assertEqual('game-data/provenance/world-east-palace-script.json',scene['unavailableRegionEvidence'])

    def test_real_enemy_stats_drops_and_full_graphics_keep_evidence_scope(self):
        r=self.reader;enemies=self.batch['combatOverlay']['enemies'][len(self.base['combatOverlay']['enemies']):]
        self.assertEqual({16,17,18,19,141},{e['id']for e in enemies})
        for enemy in enemies:
            original=extract_enemy(r,enemy['id']);tail=original['remainingBytes']
            for key in ['hp','attack','defense','experienceReward','moneyReward']:
                self.assertEqual(original[key],enemy[key])
            self.assertEqual((tail[1],tail[2]),(enemy['behaviorByte'],enemy['hitByte']))
            category={0:'medicine',2:'weapon',3:'armor'}[tail[4]]
            self.assertEqual({'category':category,'itemId':f'rom.{category}.{tail[5]}','threshold':tail[3]},enemy['loot'])
            asset=f'enemy-{enemy["id"]}.png';recipe=self.batch['graphics'][asset]
            self.assertEqual(scoped_observed_graphic(r,recipe),scoped_observed_graphic(r,recipe))
        controlled=self.batch['graphics']['enemy-19.png']
        self.assertFalse(controlled['normalPlayEvidence'])
        self.assertEqual('CONTROLLED_ORIGINAL_FULL_GROUP_LOADER',controlled['captureKind'])
        self.assertEqual((8,2,80,19),(controlled['sourceZone'],controlled['sourceGroup'],controlled['sourceType'],controlled['enemyId']))
        self.assertEqual('UNKNOWN',next(e for e in enemies if e['id']==19)['nameEvidence']['kind'])
        defined={i['id']for i in self.batch['items']}|{'rom.medicine.0'}
        self.assertTrue({e['loot']['itemId']for e in enemies}<=defined)
        item=next(i for i in self.batch['items']if i['id']=='rom.medicine.7')
        self.assertEqual((15,7),(item['buyPrice'],item['sellPrice']))
        self.assertFalse(any(k in item for k in ['herbUse','antidoteUse','worldUse']))

    def test_story_preserves_witnessed_order_and_atomic_join_without_extra_grants(self):
        boss=next(b for b in self.batch['combatOverlay']['bosses']if b['id']=='rom.boss.141')
        continuation=boss['continuation'];rules=self.script['rules']
        self.assertEqual((46,1,157,141,'rom.map.95.flag.1'),
            (boss['eventId'],boss['eventArgument'],boss['sourceType'],boss['enemyId'],boss['flagId']))
        self.assertEqual([f'rom.dialogue.105.{i}'for i in [3,4,5,6,7,8,9,11,12,15,13,14]],continuation['dialogueIds'])
        self.assertEqual(rules['dialogueIds'],continuation['dialogueIds'])
        self.assertEqual({'mapId':23,'x':54,'y':92,'encounterSteps':0},continuation['destination'])
        self.assertEqual(['rom.map.95.flag.128'],continuation['completionFlags'])
        self.assertNotIn('direction',continuation['destination']);self.assertNotIn('terrainMode',continuation['destination'])
        self.assertNotIn('reward',continuation);self.assertNotIn('inventory',continuation)
        self.assertEqual(98,self.script['dialogueMapCheck']['sampleCount'])
        self.assertEqual(95,rules['dialogueMapId'])
        npc=next(n for n in self.batch['npcs']if n['id']=='rom.npc.95.0')
        self.assertEqual('rom.dialogue.105.16',npc['repeatDialogue']);self.assertEqual([],npc['firstEffects'])
        repeat=next(d for d in self.batch['dialogues']if d['id']==npc['repeatDialogue'])
        self.assertTrue(repeat['source']['placeholder']);self.assertFalse(repeat['source']['originalVerified'])

    def test_joined_character_uses_own_growth_multiplier_and_original_equipment(self):
        actor=self.batch['additionalCharacters'][0];original=self.party['initialCharacter']
        self.assertEqual(1,actor['originalActorIndex'])
        for key,value in actor['initialState'].items():self.assertEqual(original[key],value,key)
        self.assertEqual('npc-129-cave.png',actor['portraitAsset'])
        growth=self.batch['combatOverlay']['characterGrowth'][0]
        self.assertEqual(('xiaolongnv',1,80),(growth['owner'],growth['originalActorIndex'],growth['knownMaxLevel']))
        rows=extract_growth_candidates(self.reader)['groups'][1]['rows'][1:]
        for raw,row in zip(rows,growth['rows']):
            self.assertEqual((raw['index']+1,raw['cumulativeExpCandidate']),(row['level'],row['threshold']))
            for field in ['hp','mp','strength','stamina','agility','spirit']:
                self.assertEqual(raw[field+'DeltaCandidate'],row[field])
            self.assertFalse(row['runtimeVerified'])
        self.assertEqual(79,len(growth['rows']))
        thresholds=self.batch['combatOverlay']['characterMultiplierThresholds']['xiaolongnv']
        self.assertEqual(list(self.reader.read(9,self.reader.word(9,0x8579),36)),thresholds)
        self.assertNotEqual(list(self.reader.read(9,self.reader.word(9,0x8577),36)),thresholds)
        for resource in self.party['items']:
            item=next(i for i in self.batch['items']if i['id']==resource['id'])
            self.assertEqual(['xiaolongnv'],item['equipment']['allowedCharacters'])
            self.assertEqual(resource['preview'],item['preview'])


if __name__=='__main__':unittest.main()
