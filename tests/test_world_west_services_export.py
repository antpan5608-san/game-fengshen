"""Original shared service callers, palace terrain and complete West encounters."""
import copy,io,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter

class WestServicesExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        path=Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk'))
        cls.base=ci.content(path,cls.pin['iteration']['base']);cls.proof=cls.pin['iteration']['provenance']
        cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
        cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_clean_repeat_preserves_every_reviewed_media_byte(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        for name,raw in self.base.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        for name,expected in json.loads(self.result['manifest.json'])['files'].items():self.assertEqual(expected,ci.sha(self.result[name]))
    def test_shared_rooms_bind_each_real_caller_without_duplicating_geometry(self):
        self.assertEqual({114,16,0,17,18,19,22,25,97,1,96},{m['id'] for m in self.scene['maps']})
        bindings=self.scene['serviceBindings'];self.assertEqual(8,len(bindings))
        for mid in (17,18,19,22):self.assertEqual({0,1},{b['callerMapId'] for b in bindings if b['interiorMapId']==mid})
        shop=next(s for s in self.scene['shops'] if s['id']=='rom.shop.1.17')
        self.assertEqual(['rom.weapon.2','rom.weapon.3'],shop['items'])
        self.assertEqual(8,next(i for i in self.scene['inns'] if i['id']=='rom.inn.1')['price'])
        edge=next(e for e in self.scene['exits'] if e['fromMapId']==1 and e['toMapId']==16)
        self.assertEqual(([15,29],[191,102],'DOWN','EDGE'),(edge['trigger'],edge['spawn'],edge['direction'],edge['triggerMode']))
    def test_equipment_and_item_rules_are_separate_from_stock(self):
        items={i['id']:i for i in self.scene['items']}
        for ident,price,slot,field,bonus in [('rom.weapon.3',200,'rightHand','attackBonus',16),('rom.armor.2',200,'body','defenseBonus',12),('rom.armor.29',500,'feet','evasionValue',5)]:
            item=items[ident];self.assertEqual(price,item['buyPrice']);self.assertEqual(slot,item['equipment']['slot']);self.assertEqual(bonus,item['equipment'][field])
            image=Image.open(io.BytesIO(self.result[item['preview']['asset']])).convert('RGBA');self.assertGreater(len(set(image.getdata())),1)
        silk=items['rom.medicine.12'];self.assertEqual((80,40,10),(silk['buyPrice'],silk['sellPrice'],silk['maxCount']))
        self.assertNotIn('herbUse',silk);self.assertNotIn('antidoteUse',silk)
        self.assertEqual(51,self.combat['physicalRules']['weaponHitThreshold']['3'])
    def test_palace_has_true_planes_complete_encounters_and_independent_boss_flag(self):
        scene=json.loads(self.result['scene96.json']);self.assertEqual(4,scene['terrain']['tileset']);self.assertIn(3,scene['walkableClasses'])
        zone=next(z for z in self.combat['zones'] if z['mapId']==96)
        self.assertEqual((3,245,'HIGH',15),(zone['id'],zone['randomThreshold'],zone['randomGate'],len(zone['groups'])))
        self.assertEqual({8,9},{m['enemyId'] for g in zone['groups'] for m in g['entities']})
        boss=next(b for b in self.combat['bosses'] if b['id']=='rom.boss.138');self.assertEqual('rom.event.96.40.2',boss['flagId'])
        enemy=next(e for e in self.combat['enemies'] if e['id']==138)
        self.assertEqual((150,21,17,80,150,10),(enemy['hp'],enemy['attack'],enemy['defense'],enemy['experienceReward'],enemy['moneyReward'],enemy['iceBaseDamage']))
        self.assertEqual('rom.dialogue.106.3',boss['victoryDialogue'])
    def test_growth_extends_same_owner_without_changing_verified_rows(self):
        old=json.loads(self.base['combat.json'])['nezhaGrowth'];rows=self.combat['nezhaGrowth']
        self.assertEqual(old,rows[:len(old)]);self.assertEqual(list(range(2,81)),[g['level'] for g in rows])
        self.assertEqual(80,self.combat['growthLimit']['level'])
    def test_corrupt_service_route_stat_and_behavior_definitions_fail_closed(self):
        for kind in ('price','stock','caller','edge','boss','ice','zone','group','equipment','hit','terrain'):
            e=copy.deepcopy(self.evidence)
            if kind=='price':e['items'][0]['buyPrice']+=1
            elif kind=='stock':e['shops'][0]['items'].pop()
            elif kind=='caller':e['serviceBindings'].append(e['serviceBindings'][0])
            elif kind=='edge':next(x for x in e['exits'] if x['kind']=='EDGE_RECORD')['trigger']=[5,5]
            elif kind=='boss':e['combatOverlay']['bosses'][0]['eventArgument']=1
            elif kind=='ice':next(x for x in e['combatOverlay']['enemies'] if x['id']==138)['iceBaseDamage']=12
            elif kind=='zone':next(x for x in e['combatOverlay']['zones'] if x['mapId']==96)['id']=255
            elif kind=='group':next(x for x in e['combatOverlay']['zones'] if x['mapId']==96)['groups'].pop()
            elif kind=='equipment':e['items'][0]['equipment']['attackBonus']=999
            elif kind=='hit':e['combatOverlay']['weaponHits'][0]['threshold']=64
            else:next(x for x in e['maps'] if x['mapId']==96)['terrain']['tileset']=0
            def read(path):return e if Path(path).resolve()==(ci.ROOT/self.proof).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin)

if __name__=='__main__':unittest.main()
