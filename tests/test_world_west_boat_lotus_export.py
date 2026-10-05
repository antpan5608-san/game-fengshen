"""Bounded original boat/fairy integration; no App voyage or full quest claim."""
import copy,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex

class WestBoatLotusExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=ex.load(ROOT/'ci/golden-world-west-boat-lotus-content.json')
        cls.parent=ex.load(ROOT/'ci/golden-world-west-scene-items-content.json')
        cls.apk=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
        cls.base=ci.content(cls.apk,cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.out=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin)
        cls.scene=json.loads(cls.out['scene.json'])
    def test_native_room_and_vehicle_refs_preserve_old_items_combat_and_media(self):
        self.assertEqual(67,len(self.scene['maps']));self.assertEqual(368,len(self.out))
        self.assertEqual(self.pin['manifestSha256'],ex.digest(self.out['manifest.json']))
        parent=json.loads(self.old['scene.json'])
        self.assertEqual(parent['items'],self.scene['items']);self.assertEqual(parent['npcs'],self.scene['npcs'][:-2])
        self.assertEqual(parent['exits'],self.scene['exits'][:-2]);self.assertEqual(parent['ferries'],self.scene['ferries'])
        for name in ['combat.json',*[v['scene']for v in parent['maps']if v['id']not in (114,16)]]:
            before=json.loads(self.old[name]);after=json.loads(self.out[name]);before.pop('version');after.pop('version')
            self.assertEqual(before,after,name)
        for name,data in self.old.items():
            if not name.endswith('.json'):self.assertEqual(data,self.out[name],name)
        self.assertEqual([0,219],[e['arrivalTerrainMode']for e in self.scene['exits'][-2:]])
        self.assertTrue(all(e['resetEncounterSteps']for e in self.scene['exits'][-2:]))
        self.assertEqual(219,self.scene['freeBoat']['mode'])
        for asset in self.scene['freeBoat']['sprites'].values():self.assertIn(asset,self.out)
        fairy=self.scene['npcs'][-2];self.assertEqual(47,fairy['originalTalk']['actionId'])
        self.assertEqual('rom.special.0',fairy['originalTalk']['itemId']);self.assertEqual([],fairy['firstEffects'])
        room=json.loads(self.out['scene136.json']);self.assertEqual(4,room['terrain']['tileset'])
        self.assertEqual({0,1,2,3},set(room['collision']));self.assertEqual([87,135],room['dynamicObjectCells'])
    def test_original_restore_reproduces_full_target_in_empty_directory(self):
        with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
            dest=Path(td)/'content';ci.restore(self.apk,dest,next_code=84)
            self.assertEqual(self.out,{p.name:p.read_bytes()for p in dest.iterdir()})
    def test_fake_boarding_lock_free_step_and_changed_fairy_or_exit_are_rejected(self):
        original=ex.load
        for mode in ('paddle-lock','free-step','death-success','gift','exit','font'):
            path=ROOT/('game-data/provenance/world-west-free-boat.json'if mode in ('paddle-lock','free-step','death-success')else'game-data/provenance/world-lotus136-resources.json')
            proof=copy.deepcopy(original(path))
            if mode=='paddle-lock':proof['rules']['paddleRequiredToBoard']=True
            elif mode=='free-step':proof['rules']['fubingBoard']['statusSteps']=0
            elif mode=='death-success':proof['rules']['allDisabledBoardFailure']['x']=68
            elif mode=='gift':proof['npcs'][0]['firstEffects']=[{'kind':'money','amount':100}]
            elif mode=='exit':proof['exits'][1]['arrivalTerrainMode']=0
            else:proof['dialogues'][0]['text']='fake cure reward'
            with patch.object(ex,'load',side_effect=lambda p:proof if Path(p)==path else original(p)),self.assertRaises(ValueError):
                ex.validate_world_lotus_resources(ex.iteration_reader())

if __name__=='__main__':unittest.main()
