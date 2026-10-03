"""Scoped room, original gift/selector proof and reproducible existing exporter."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex

class Room171ExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-room171-content.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-room171-content.json'
        cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin)
    def test_room_has_actual_actor_positions_two_independent_exits_and_no_invented_encounters(self):
        s=json.loads(self.result['scene.json']);m=json.loads(self.result['scene171.json'])
        self.assertEqual((16,15,[0,2]),(m['width'],m['height'],m['walkableClasses']))
        self.assertEqual(39,len(s['maps']));self.assertEqual(215,len(self.result))
        exits=[e for e in s['exits']if 171 in(e['fromMapId'],e['toMapId'])]
        self.assertEqual([(101,[32,12],171,[7,14]),(171,[7,14],101,[32,12])],
                         [(e['fromMapId'],e['trigger'],e['toMapId'],e['spawn'])for e in exits])
        self.assertTrue(all(e['preserveArrivalDirection']and e['resetEncounterSteps']for e in exits))
        actors=[n for n in s['npcs']if n['mapId']==171]
        self.assertEqual([[10,6],[7,3],[5,8]],[n['cell']for n in actors])
        self.assertEqual(([7,5],'UP'),(actors[1]['interactionCell'],actors[1]['interactionDirection']))
        self.assertEqual(12,actors[1]['originalTalk']['actionId']);self.assertEqual(11,actors[0]['originalTalk']['actionId'])
        self.assertFalse(any(z['mapId']==171 for z in json.loads(self.result['combat.json'])['zones']))
    def test_signal_has_actual_capacity_identity_and_no_inferred_price_or_use(self):
        s=json.loads(self.result['scene.json']);i=next(i for i in s['items']if i['id']=='rom.special.19')
        self.assertEqual(('special',19,1,'玉佩'),tuple(i[k]for k in ['category','originalId','maxCount','name']))
        for key in ['buyPrice','sellPrice','worldUse','fieldProtectionUse']:self.assertNotIn(key,i)
        self.assertEqual('PROVISIONAL_REFERENCE',i['source']['confidence'])
        teacher=next(n for n in s['npcs']if n['id']=='rom.npc.171.1')
        self.assertEqual([],teacher['firstEffects']);self.assertEqual('rom.dialogue.181.3',teacher['repeatDialogue'])
        self.assertEqual({str(k):f'rom.dialogue.181.{k}'for k in [0,1,2,3,7]},teacher['originalTalk']['messageDialogues'])
        ci.validate_item_sources(self.result)
    def test_old_media_combat_and_definitions_are_preserved_and_export_repeats(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        old=json.loads(self.old['combat.json']);new=json.loads(self.result['combat.json']);old['version']=new['version']
        self.assertEqual(old,new)
        old=json.loads(self.old['scene.json']);new=json.loads(self.result['scene.json'])
        for key in ['items','npcs','dialogues']:
            lookup={x['id']:x for x in new[key]}
            for x in old[key]:self.assertEqual(x,lookup[x['id']])
    def test_open_walls_wrong_target_effect_or_gift_price_is_rejected(self):
        for kind in ['walls','counter','flag','price','use','effect']:
            p=copy.deepcopy(self.p)
            if kind=='walls':p['maps'][0]['walkableClasses'].append(1)
            elif kind=='counter':p['npcs'][1]['interactionCell']=[7,4]
            elif kind=='flag':p['npcs'][1]['originalTalk']['mapFlagId']='rom.map.171.flag.1'
            elif kind=='price':p['items'][0]['buyPrice']=1
            elif kind=='use':p['items'][0]['worldUse']={'targetSpriteId':130}
            else:p['npcs'][1]['firstEffects']=[{'kind':'money','amount':100}]
            def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=kind):
                ex.export_from_base(self.base,self.path,self.pin,verify_target=False)

if __name__=='__main__':unittest.main()
