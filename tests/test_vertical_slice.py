"""Original V1 goldens: synthetic contracts plus optional local, offline ROM/captures."""
import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.common import ROOT,load
from forensics.fengshen246 import Reader,SHA256,digest,extract_map,extract_npcs,extract_enemy,extract_growth_candidates,verify_enemy_runtime
from forensics.vertical_slice import (opening_font,opening_dialogues,npc_fields,collision_kind,
    collision_check,verify_world_viewport,character_state,verify_growth_pair,transition_check,enemy_classification,FONT0)
from forensics.validator import validate_original_artifacts


class V1ContractTests(unittest.TestCase):
    def test_font_context_is_bounded_not_global(self):
        self.assertEqual([opening_font(i) for i in range(12)],[0]*10+[78]*2)
        for i in (-1,12,124):
            with self.assertRaises(ValueError):opening_font(i)
        self.assertEqual([FONT0[k] for k in (0x83,0xab,0x23,0x34,0x96)],['百','有','体','體','牠'])

    def test_npc_coordinate_contract_and_animation_not_event(self):
        b=bytearray(14);b[0]=0xa8;b[1:4]=bytes([10,11,7]);b[4:6]=(248).to_bytes(2,'little');b[6:8]=(360).to_bytes(2,'little')
        b[8:10]=(0x936d).to_bytes(2,'little');b[10]=0x81;b[12:14]=bytes([3,1])
        n=npc_fields({'rawHex':b.hex(),'index':0})
        self.assertEqual(n['metatile'],[8,15]);self.assertEqual(n['mapPixelAnchor'],[136,248])
        self.assertEqual(n['spriteId'],40);self.assertEqual(n['direction'],'DOWN')
        self.assertEqual(n['animationModule'],0);self.assertEqual(n['animationProgramPointer'],0x936d)
        self.assertEqual((n['eventSelector'],n['eventFlagMask']),(3,1))

    def test_collision_door_is_not_transition_and_unknown_stays_unknown(self):
        self.assertEqual([collision_kind(4,c) for c in range(4)],['WALKABLE','BLOCKED','DOOR_OCCLUSION','UNKNOWN'])
        self.assertEqual([c for c in range(27) if collision_kind(1,c)=='BLOCKED'],[1,3,4,5,7])
        self.assertEqual(collision_kind(1,27),'UNKNOWN');self.assertEqual(collision_kind(2,0),'UNKNOWN')

    def test_uint24_exp_money_and_snapshot_bounds(self):
        r=bytearray(2048);s=bytearray(2048);r[0x508:0x50b]=bytes([1,2,3]);r[0x501:0x504]=bytes([4,5,6]);s[0x130:0x133]=bytes([7,8,9])
        state=character_state(r,s)
        self.assertEqual((state['xp'],state['money'],state['remainingExp']),(0x030201,0x060504,0x090807))
        self.assertEqual(state['level'],1)
        with self.assertRaises(ValueError):character_state(b'',s)

    def test_growth_verifier_rejects_xp_reset_wrong_delta_and_level(self):
        before={'maxHp':20,'maxMp':0,'strength':8,'stamina':4,'agility':2,'spirit':4,'hp':1,'xp':13,'level':2,'remainingExp':14}
        after={**before,'maxHp':23,'strength':10,'stamina':5,'agility':3,'hp':4}
        row={'index':1,'hpDeltaCandidate':3,'mpDeltaCandidate':0,'strengthDeltaCandidate':2,'staminaDeltaCandidate':1,'agilityDeltaCandidate':1,'spiritDeltaCandidate':0,'cumulativeExpCandidate':12}
        self.assertTrue(verify_growth_pair(before,after,row,27)['passed'])
        for field,value in [('xp',1),('maxHp',24),('remainingExp',7),('level',3)]:
            bad={**after,field:value};self.assertFalse(verify_growth_pair(before,bad,row,27)['passed'])

    def test_enemy_numeric_signature_never_verifies_identity(self):
        e={'romEnemyId':4,'hp':5,'attack':6,'defense':7,'experienceReward':8,'moneyReward':9}
        raw={'records':[{'id':f'reference.monster.{i}','table':'monster','data':{'hp':5,'atk':6,'def':7,'exp':8,'money':9}} for i in (1,2)]}
        result=enemy_classification([e],raw)
        self.assertEqual(len(result['duplicateReferenceSignatures']),1)
        self.assertTrue(all(not x['originalVerified'] for x in result['records']))
        self.assertTrue(all(x['candidateRomIds']==[4] for x in result['records']))


class LocalV1GoldenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        files=[p for p in (ROOT/'reference/rom').rglob('*') if p.is_file() and p.suffix.lower()=='.nes']
        p=next((p for p in files if digest(p.read_bytes())==SHA256),None)
        if p is None:raise unittest.SkipTest('Pinned private ROM unavailable; no download')
        cls.reader=Reader(p.read_bytes());cls.maps={i:extract_map(cls.reader,i) for i in (114,16)}

    def local(self,name):
        p=ROOT/name
        if not p.exists():self.skipTest('Private capture unavailable: '+name)
        return p

    def test_charset_bank_rom_switch_operands(self):
        r=self.reader
        self.assertEqual(r.read(2,0xb338,3),bytes.fromhex('ad7506'))
        self.assertEqual(r.read(2,0xb33d,2),bytes.fromhex('c972'))
        self.assertEqual(r.read(2,0xcfe8+114)[0],0)
        self.assertIn(bytes.fromhex('a94e'),r.read(2,0xb338,0x60))

    def test_dialogue_opening_twelve_streams_golden(self):
        ds=opening_dialogues(self.reader)
        self.assertEqual(len(ds),12);self.assertTrue(all(not d['unknownCodes'] for d in ds))
        self.assertEqual(ds[1]['text'],'『兒呀！娘好擔心你，你可要好好保重自己！這裏有一百兩，你留著用吧！』')
        self.assertEqual(ds[8]['text'],'『服用藥草可以恢復体力。』')
        self.assertIn('都是你做的好事',ds[10]['text']);self.assertNotIn('都是你惹的好事',ds[10]['text'])
        self.assertEqual(ds[11]['text'].strip(),'『你走吧！』')
        self.assertTrue(all(d['rawHex'].endswith('c3') for d in ds))

    def test_npc_ten_copies_and_player_coordinate_golden(self):
        ram=self.local('private-derived/slice-probe/frame-0450-ram.bin').read_bytes()
        npcs=[npc_fields(n) for n in extract_npcs(self.reader)['records']]
        self.assertEqual(len(npcs),10)
        for n in npcs:
            a=0x402+22*n['runtimeSlot'];self.assertEqual(ram[a:a+14].hex(),n['rawHex'])
        self.assertEqual([n['metatile'] for n in npcs[:2]],[[8,15],[7,15]])
        self.assertEqual([(int.from_bytes(ram[a:a+2],'little')-120)//16 for a in (0x406,0x408)],[8,21])
        self.assertEqual([(n['dialogueFirst'],n['dialogueRepeat']) for n in npcs[:2]],[(10,11),(1,0)])

    def test_collision_rom_runtime_golden_and_tamper(self):
        total=0
        for name in ('slice-probe','world-probe'):
            txt=self.local(f'private-derived/{name}/collision.tsv').read_text()
            c=collision_check(self.reader,self.maps,txt);self.assertTrue(c['passed']);total+=c['checks']
            lines=txt.splitlines();a=lines[0].split('\t');a[7]=str(1-int(a[7]));lines[0]='\t'.join(a)
            self.assertFalse(collision_check(self.reader,self.maps,'\n'.join(lines))['passed'])
        self.assertEqual(total,381)

    def test_map_transition_golden_and_uninitialized_spawn_rejected(self):
        txt=self.local('private-derived/world-probe/trajectory.tsv').read_text();t=transition_check(self.reader,txt)
        self.assertTrue(t['runtimePassed']);self.assertEqual(t['spawnObservation'][0],888)
        self.assertEqual(t['destinationPlayerMetatile'],[203,142]);self.assertEqual(t['rawHex'],'081d10cb8e')
        no_spawn='\n'.join(line for line in txt.splitlines() if not line.startswith('888\t'))
        self.assertFalse(transition_check(self.reader,no_spawn)['runtimePassed'])

    def test_world_extension_ppu_golden_and_tamper(self):
        m=self.maps[16];self.assertEqual((m['width'],m['height'],len(m['chunks'])),(256,181,208))
        self.assertEqual({c['module'] for c in m['chunks'] if c['chunkY']<10},{4})
        self.assertEqual({c['module'] for c in m['chunks'] if c['chunkY']>=10},{15})
        self.assertEqual({c['rows'] for c in m['chunks'] if c['chunkY']==12},{1})
        p=bytearray(self.local('private-derived/world-probe/frame-0960-ppu.bin').read_bytes())
        self.assertTrue(verify_world_viewport(m,p)['passed']);p[0x228a]^=1
        self.assertFalse(verify_world_viewport(m,p)['passed'])

    def test_level_progression_golden_first_and_second(self):
        g=extract_growth_candidates(self.reader)['groups'][0]['rows']
        for frame,branch,index in [(13121,1,1),(39282,5,2)]:
            states=[]
            for label in ('growth-entry','change'):
                base=f'private-derived/level-probe/{frame:06d}-b{branch}-{label}'
                states.append(character_state(self.local(base+'-ram.bin').read_bytes(),self.local(base+'-sram.bin').read_bytes()))
            checked=verify_growth_pair(*states,g[index],g[index+1]['cumulativeExpCandidate'])
            self.assertTrue(checked['passed']);self.assertEqual(states[1]['maxHp'],20+index*3)
        log=self.local('private-derived/level-probe/events.tsv').read_text()
        self.assertIn('10682\t1\tlevel-check-entry\t8\t0\t8\t',log)
        self.assertIn('13121\t1\tlevel-check-entry\t8\t0\t13\t',log)
        self.assertIn('39282\t5\tlevel-check-entry\t8\t1\t27\t',log)
        self.assertEqual([g[i]['cumulativeExpCandidate'] for i in (1,2,3)],[12,27,80])
        self.assertEqual(self.reader.read(0,0xb814,5),bytes.fromhex('a9078d3069'))

    def test_first_battle_reward_golden(self):
        b=self.local('private-derived/battle-probe/frame-1980-ram.bin').read_bytes()
        a=self.local('private-derived/battle-probe/frame-5400-ram.bin').read_bytes()
        val=lambda data,start,n:int.from_bytes(data[start:start+n],'little')
        self.assertEqual((val(b,0x514,2),val(a,0x514,2)),(20,8))
        self.assertEqual(val(a,0x508,3)-val(b,0x508,3),5)
        self.assertEqual(val(a,0x501,3)-val(b,0x501,3),3)

    def test_enemy_enumeration_and_third_runtime_enemy(self):
        enemies=[extract_enemy(self.reader,i) for i in range(177)]
        self.assertEqual(len({e['range']['offset'] for e in enemies}),177)
        self.assertEqual(enemies[0]['range']['cpuAddress'],0xebba)
        self.assertEqual(enemies[-1]['range']['cpuAddress']+16,0xf6ca)
        with self.assertRaises(ValueError):extract_enemy(self.reader,177)
        s=self.local('private-derived/level-probe/007500-b0-periodic-sram.bin').read_bytes()
        self.assertTrue(verify_enemy_runtime(enemies[1],s,5)['passed'])

    def test_v1_evidence_and_canonical_still_blocked(self):
        src=load(self.local('game-data/provenance/original.json'))
        audit=validate_original_artifacts(src);self.assertEqual(audit['status'],'PASS')
        ids={r['id'] for r in src['records']}
        for r in src['records']:
            for ref in r['evidenceRefs']:self.assertIn(ref,ids)
        official=load(ROOT/'game-data/canonical/baseline.json')
        self.assertFalse(official['runtimeReady']);self.assertFalse(any(official['entities'].values()))
        c=load(self.local('game-data/canonical-candidate/vertical-slice.json'))
        self.assertFalse(c['runtimeConsumable']);self.assertEqual(c['status'],'RESEARCH_ONLY')

    def test_extraction_regression_grid_digests(self):
        # Fixed independently recorded pre-V1 initial grid and V1 world grid.
        self.assertEqual(self.maps[114]['gridSha256'],'c39ee79af3560c157b78747693b93d7914140f38037c5c218dbdbeb2c663e25f')
        self.assertEqual(self.maps[16]['gridSha256'],'4a482d209cca015198806a5da5d5c697a905fe710e846b9ce26222882e2406c4')
