"""Original cave, actual position script and immutable scoped content regression."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter

class Cave85ExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        cls.proof=cls.pin['iteration']['provenance']
        cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK',
            '/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
        cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_clean_repeatable_export_and_existing_media_unchanged(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        oldpin=json.loads((ci.ROOT/'ci/golden-world-north-palace-content.json').read_text(encoding='utf-8'))
        old=exporter.export_from_base(self.base,oldpin['iteration']['provenance'],oldpin)
        for name,data in old.items():
            if not name.endswith('.json'):self.assertEqual(data,self.result[name],name)
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
        for name,sha in json.loads(self.result['manifest.json'])['files'].items():self.assertEqual(sha,ci.sha(self.result[name]))
    def test_real_directed_cave_and_adjacent_sea_connections(self):
        edges={(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in self.scene['exits']}
        self.assertTrue({(25,(53,30),16,(213,118)),(16,(213,118),25,(53,30)),
            (16,(212,114),85,(30,29)),(85,(30,29),16,(212,114)),
            (85,(2,2),16,(215,106)),(16,(215,106),85,(2,2)),
            (16,(214,110),25,(54,22)),(25,(54,22),16,(214,110))}<=edges)
    def test_cave_flat_terrain_and_complete_original_encounters(self):
        cave=json.loads(self.result['scene85.json'])
        self.assertEqual((32,30),(cave['width'],cave['height']));self.assertEqual([0,13],cave['walkableClasses'])
        self.assertNotIn('terrain',cave) # Do not apply the palace plane profile to a cave.
        self.assertEqual({0,1,13},set(cave['collision']))
        zone=next(z for z in self.combat['zones']if z['mapId']==85)
        self.assertEqual((6,245,'HIGH'),(zone['id'],zone['randomThreshold'],zone['randomGate']))
        self.assertEqual(12,len(zone['groups']));self.assertEqual({14,15},{e['enemyId']for g in zone['groups']for e in g['entities']})
    def test_script_actor_is_dynamic_and_not_an_invented_treasure_or_gate(self):
        boss=next(b for b in self.combat['bosses']if b['id']=='rom.boss.140')
        actor=next(n for n in self.scene['npcs']if n['id']==boss['npcId'])
        self.assertEqual((85,2,6),(boss['entryTrigger']['mapId'],boss['entryTrigger']['x'],boss['entryTrigger']['y']))
        self.assertEqual((5,158,140),(boss['eventId'],boss['sourceType'],boss['enemyId']))
        self.assertEqual('rom.map.85.flag.128',boss['flagId']);self.assertTrue(boss['commitAfterDialogue'])
        self.assertTrue(actor['scriptedActor']);self.assertEqual([2,5],actor['cell']);self.assertEqual([],actor['firstEffects'])
        self.assertNotIn('treasure',actor);self.assertNotIn('requiredItems',boss);self.assertNotIn('requiredLevel',boss)
        cave=json.loads(self.result['scene85.json']);self.assertNotIn(5*32+2,cave['dynamicObjectCells'])
    def test_actual_boss_stats_rewards_and_unsupported_drop_effect(self):
        e=next(e for e in self.combat['enemies']if e['id']==140)
        self.assertEqual((240,34,23,142,240,0,230),tuple(e[k]for k in ['hp','attack','defense','experienceReward','moneyReward','behaviorByte','hitByte']))
        self.assertEqual({'category':'medicine','itemId':'rom.medicine.9','threshold':10},e['loot'])
        item=next(i for i in self.scene['items']if i['id']=='rom.medicine.9')
        self.assertEqual('PROVISIONAL_REFERENCE',item['source']['confidence'])
        self.assertNotIn('herbUse',item);self.assertNotIn('antidoteUse',item)
    def test_wrong_trigger_source_actor_or_reward_rejected_before_build(self):
        for kind in ('trigger','source','actor','reward','phase','missing-group'):
            evidence=copy.deepcopy(self.evidence)
            boss=next(b for b in evidence['combatOverlay']['bosses']if b['id']=='rom.boss.140')
            if kind=='trigger':boss['entryTrigger']['y']=5
            elif kind=='source':boss['sourceType']=154
            elif kind=='actor':next(n for n in evidence['npcs']if n['id']==boss['npcId'])['cell']=[2,4]
            elif kind=='reward':next(e for e in evidence['combatOverlay']['enemies']if e['id']==140)['moneyReward']=1000
            elif kind=='phase':boss['commitAfterDialogue']=False
            else:next(z for z in evidence['combatOverlay']['zones']if z['mapId']==85)['groups'].pop()
            def read(path):return evidence if Path(path).resolve()==(ci.ROOT/self.proof).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin)

if __name__=='__main__':unittest.main()
