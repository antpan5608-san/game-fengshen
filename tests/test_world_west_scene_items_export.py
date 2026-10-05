"""Native scene effects enter the original baseExport; not an App travel claim."""
import copy,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import ci_apk as ci
import export_development as ex

class WestSceneItemsExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=ex.load(ROOT/'ci/golden-world-west-scene-items-content.json')
        cls.parent=ex.load(ROOT/'ci/golden-world-west-houses-content.json')
        cls.apk=Path('/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')
        cls.base=ci.content(cls.apk,cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.out=ex.export_from_base(cls.base,cls.pin['iteration']['provenance'],cls.pin)
        cls.scene=json.loads(cls.out['scene.json'])
    def test_scene_effects_preserve_maps_encounters_media_and_original_prices(self):
        self.assertEqual(66,len(self.scene['maps']));self.assertEqual(360,len(self.out))
        self.assertEqual(self.pin['manifestSha256'],ex.digest(self.out['manifest.json']))
        before=json.loads(self.old['scene.json']);self.assertEqual(before['maps'],self.scene['maps'])
        self.assertEqual(before['exits'],self.scene['exits']);self.assertEqual(before['npcs'],self.scene['npcs'])
        oldCombat=json.loads(self.old['combat.json']);newCombat=json.loads(self.out['combat.json'])
        self.assertEqual(self.parent['contentVersion'],oldCombat.pop('version'))
        self.assertEqual(self.pin['contentVersion'],newCombat.pop('version'))
        self.assertEqual(oldCombat,newCombat) # Every actual rule, group, stat, drop and source is unchanged.
        for name,data in self.old.items():
            if not name.endswith('.json'):self.assertEqual(data,self.out[name],name)
        items={i['id']:i for i in self.scene['items']}
        for old in before['items']:
            if old['id']=='rom.special.14':
                for key,value in old.items():
                    if key not in ('name','description'):self.assertEqual(value,items[old['id']][key],key)
            else:self.assertEqual(old,items[old['id']])
        self.assertEqual('神木槳',items['rom.special.14']['name'])
        self.assertEqual('雪蓮',items['rom.special.0']['name'])
        for id,actor in [(0,130),(14,162)]:
            i=items[f'rom.special.{id}'];self.assertEqual(dict(targetSpriteId=actor,reusable=False,
                usedFlagId=f'rom.inventory.special.{id}.used',evidence='game-data/provenance/world-west-scene-items.json'),i['worldUse'])
            self.assertNotIn('buyPrice',i);self.assertNotIn('sellPrice',i)
        self.assertNotIn(136,{m['id']for m in self.scene['maps']}) # Voyage and fairy remain a separate unfinished input.
    def test_original_restore_reproduces_full_target_in_empty_directory(self):
        with tempfile.TemporaryDirectory()as td,patch.object(ci,'CONFIG',self.pin):
            dest=Path(td)/'content';ci.restore(self.apk,dest,next_code=84)
            self.assertEqual(self.out,{p.name:p.read_bytes()for p in dest.iterdir()})
    def test_changed_consumption_dialogue_hp_formula_and_parent_are_rejected(self):
        original=ex.load;path=ROOT/'game-data/provenance/world-west-scene-items.json'
        for mode in ('consume','heal','gift','text'):
            proof=copy.deepcopy(original(path))
            if mode=='consume':proof['rules'][1]['consumeCount']=0
            elif mode=='heal':proof['rules'][0]['effects']['hpFrom']='fixed50'
            elif mode=='gift':proof['items'][0]['buyPrice']=15
            else:proof['dialogues'][0]['text']='fake reward'
            with patch.object(ex,'load',side_effect=lambda p:proof if Path(p)==path else original(p)),self.assertRaises(ValueError):
                ex.validate_world_scene_items(ex.iteration_reader())
        recipePath=ROOT/self.pin['iteration']['provenance'];recipe=copy.deepcopy(original(recipePath))
        recipe['existingItemCapabilityUpdates'][0]['baseDefinitionSha256']='0'*64
        with patch.object(ex,'load',side_effect=lambda p:recipe if Path(p)==recipePath else original(p)),self.assertRaises(ValueError):
            ex.export_from_base(self.base,self.pin['iteration']['provenance'],self.pin)

if __name__=='__main__':unittest.main()
