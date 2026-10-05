"""Existing exporter/restore on a fixed reviewed APK; not Android acceptance."""
import copy, json, os, sys, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex

class JiamengExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=ex.load(ROOT/'ci/golden-world-jiameng-content.json')
        cls.path=Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK',
            '/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk'))
        cls.base=ci.content(cls.path,cls.pin['iteration']['base'])
        parent=ex.load(ROOT/'ci/golden-world-room116-content.json')
        cls.parent=ex.export_from_base(cls.base,parent['iteration']['provenance'],parent)
        cls.payload=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin)
        cls.scene=json.loads(cls.payload['scene.json']);cls.combat=json.loads(cls.payload['combat.json'])

    def test_strict_target_and_original_media_bytes_and_new_maps(self):
        self.assertEqual(325,len(self.payload));self.assertEqual(self.pin['manifestSha256'],ex.digest(self.payload['manifest.json']))
        old=json.loads(self.parent['scene.json']);old_ids={m['id']for m in old['maps']}
        self.assertEqual(old_ids|{145,146,147,148,37},{m['id']for m in self.scene['maps']})
        self.assertEqual(61,len(self.scene['maps']))
        for name,raw in self.parent.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.payload[name],name)
        manifest=json.loads(self.payload['manifest.json'])
        self.assertEqual({n:ex.digest(raw)for n,raw in self.payload.items()if n!='manifest.json'},manifest['files'])
        ci.validate_item_sources(self.payload)

    def test_complete_original_encounters_two_bosses_and_no_extra_rewards(self):
        res=ex.load(ROOT/'game-data/provenance/world-jiameng-resources.json')
        for mid in (145,146,147,148):
            zone=next(z for z in self.combat['zones']if z['mapId']==mid)
            self.assertEqual((29,245,'HIGH',12),(zone['id'],zone['randomThreshold'],zone['randomGate'],len(zone['groups'])))
            self.assertEqual(res['zone29']['groups']['groups'],zone['groups'])
        self.assertFalse(any(z['mapId']==37 for z in self.combat['zones']))
        for boss in ex.world_jiameng_boss_definitions(ex.iteration_reader()):
            self.assertEqual(boss,next(b for b in self.combat['bosses']if b['id']==boss['id']))
        old=json.loads(self.parent['scene.json']);prior=next(i for i in old['items']if i['id']=='rom.special.18')
        current=next(i for i in self.scene['items']if i['id']==prior['id'])
        self.assertEqual(prior,{k:v for k,v in current.items()if k!='battleBindingUse'})
        self.assertEqual(5,current['battleBindingUse']['bindingMarker'])
        self.assertEqual('rom.armor.20',next(e for e in self.combat['enemies']if e['id']==62)['loot']['itemId'])
        self.assertFalse('equipment'in next(i for i in self.scene['items']if i['id']=='rom.armor.20'))

    def test_original_actor_contexts_room_and_independent_exit_rows(self):
        proof=ex.validate_world_jiameng_actors(ex.iteration_reader())
        for n in proof['npcs']:self.assertIn(n,self.scene['npcs'])
        for b in proof['sceneBarriers']:self.assertIn(b,self.scene['sceneBarriers'])
        room=json.loads(self.payload['scene37.json']);self.assertNotIn('terrain',room)
        self.assertEqual([0,2,5],room['walkableClasses']);self.assertIn(5*16+3,room['dynamicObjectCells'])
        exits=ex.load(ROOT/self.pin['iteration']['provenance'])['exits'];self.assertEqual(17,len(exits))
        for exit in exits:
            raw=ex.checked_span(ex.iteration_reader(),exit['source'])
            self.assertEqual(bytes(exit['trigger']+[exit['toMapId']]+exit['spawn']),raw)
            self.assertTrue(exit['preserveArrivalDirection'])

    def test_existing_restore_reproduces_from_empty_directory(self):
        with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
            target=Path(td)/'content';ci.restore(self.path,target,next_code=84)
            self.assertEqual(self.payload,{p.name:p.read_bytes()for p in target.iterdir()})
        bad=copy.deepcopy(self.pin);bad['manifestSha256']='0'*64
        with self.assertRaisesRegex(ValueError,'target pin'):
            ex.export_from_base(self.base,bad['iteration']['provenance'],bad)

    def test_actor_effects_barrier_flag_and_bed_context_tampering_reject(self):
        original=ex.load;path=ROOT/'game-data/provenance/world-jiameng-actors.json'
        for mode in ('reward','position','dialogue','barrier','bed','cure'):
            p=copy.deepcopy(original(path))
            if mode=='reward':p['npcs'][0]['firstEffects']=[dict(type='money',amount=1)]
            elif mode=='position':p['npcs'][0]['cell']=[5,10]
            elif mode=='dialogue':p['npcs'][5]['repeatDialogue']='rom.dialogue.47.0'
            elif mode=='barrier':p['sceneBarriers'][0]['removedFlagId']='rom.map.145.flag.2'
            elif mode=='bed':p['npcs'][7]['visibleFlagId']='rom.map.148.flag.128'
            else:p['npcs'][5]['originalTalk']['itemId']='rom.special.18'
            with patch.object(ex,'load',side_effect=lambda f:p if Path(f)==path else original(f)),self.assertRaises(ValueError):
                ex.validate_world_jiameng_actors(ex.iteration_reader())

if __name__=='__main__':unittest.main()
