"""Original item19 event and third actor on the reviewed immutable media."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
class YangJoinExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pin=ci.ROOT/'ci/golden-world-yang-join-content.json'
        cls.pin=json.loads((pin if pin.exists()else ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        cls.path='game-data/provenance/world-yang-join-content.json';cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent)
        cls.result=ex.export_from_base(cls.base,cls.path,cls.pin)
    def test_original_join_and_target_are_real_and_unrelated_media_stays_byte_identical(self):
        s=json.loads(self.result['scene.json']);self.assertEqual((215,39),(len(self.result),len(s['maps'])))
        actor=next(a for a in s['additionalCharacters']if a['initialState']['id']=='yangjian')
        state=actor['initialState'];self.assertEqual((24,26000,495,54,96,60,28,69),tuple(state[k]for k in ('level','experience','hp','mp','strength','stamina','agility','spirit')))
        self.assertEqual({'rightHand':33,'leftHand':33,'body':18,'feet':29},state['equipment'])
        self.assertEqual('npc-tree-yang130.png',actor['portraitAsset']);self.assertFalse(actor.get('skillRefs'))
        target=next(n for n in s['npcs']if n['id']=='rom.npc.110.0')['worldItemTarget']
        self.assertEqual(('rom.npccontext.110.207','rom.map.110.flag.128'),(target['removedFlagId'],target['completionFlagId']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        ci.validate_item_sources(self.result)
    def test_item_quantity_retention_and_paired_equipment_capability_do_not_invent_prices_or_single_hand_removal(self):
        s=json.loads(self.result['scene.json']);items={i['id']:i for i in s['items']}
        signal=items['rom.special.19'];self.assertEqual(1,signal['maxCount']);self.assertTrue(signal['worldUse']['reusable'])
        for k in ['buyPrice','sellPrice']:self.assertNotIn(k,signal)
        weapon=items['rom.weapon.33']['equipment'];self.assertEqual(70,weapon['attackBonus']);self.assertFalse(weapon['operationEnabled']);self.assertTrue(weapon['crossHandOccupancy'])
        self.assertEqual(['nezha','yangjian'],items['rom.armor.29']['equipment']['allowedCharacters'])
        self.assertEqual(50,items['rom.armor.18']['equipment']['defenseBonus'])
        self.assertEqual('PROVISIONAL_REFERENCE',items['rom.armor.18']['source']['confidence'])
        old=json.loads(self.old['scene.json']);current={i['id']:i for i in s['items']}
        changed={'rom.special.19','rom.weapon.33','rom.armor.29'}
        for item in old['items']:
            if item['id'] not in changed:self.assertEqual(item,current[item['id']])
    def test_own_growth_and_multiplier_are_complete_not_borrowed_and_export_repeats(self):
        c=json.loads(self.result['combat.json']);table=next(t for t in c['characterGrowth']if t['owner']=='yangjian')
        self.assertEqual((2,80,79),(table['originalActorIndex'],table['knownMaxLevel'],len(table['rows'])))
        self.assertEqual('game-data/provenance/world-party-yangjian.json',table['limitEvidence'])
        rows={r['level']:r for r in table['rows']};self.assertEqual(25870,rows[24]['threshold']);self.assertEqual(29899,rows[25]['threshold'])
        self.assertEqual(36,len(c['physicalRules']['characterMultiplierThresholds']['yangjian']))
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
        old=json.loads(self.old['combat.json']);new=copy.deepcopy(c);new['version']=old['version']
        new['characterGrowth']=[t for t in new['characterGrowth']if t['owner']!='yangjian']
        del new['physicalRules']['characterMultiplierThresholds']['yangjian'];new['physicalRules']['weaponHitThreshold'].pop('33',None)
        self.assertEqual(old,new)
    def test_wrong_actor_stats_old_owner_loss_consumption_and_wrong_context_are_rejected(self):
        for kind in ('strength','spells','count','context','paired','owner','parent','cap'):
            p=copy.deepcopy(self.p)
            if kind=='strength':p['additionalCharacters'][0]['initialState']['strength']=28
            elif kind=='spells':p['additionalCharacters'][0]['skillRefs']=['invented']
            elif kind=='count':next(u for u in p['existingItemCapabilityUpdates']if u['id']=='rom.special.19')['fields']['worldUse']['reusable']=False
            elif kind=='context':p['existingNpcCapabilityUpdates'][0]['fields']['worldItemTarget']['removedFlagId']='rom.map.110.flag.2'
            elif kind=='paired':next(u for u in p['existingItemCapabilityUpdates']if u['id']=='rom.weapon.33')['fields']['equipment']['operationEnabled']=True
            elif kind=='owner':next(u for u in p['existingItemCapabilityUpdates']if u['id']=='rom.armor.29')['fields']['equipment']['allowedCharacters']=['yangjian']
            elif kind=='cap':p['combatOverlay']['characterGrowth'][0].pop('limitEvidence')
            else:p['existingNpcCapabilityUpdates'][0]['baseDefinitionSha256']='0'*64
            def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=kind):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
if __name__=='__main__':unittest.main()
