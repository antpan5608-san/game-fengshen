"""Real original region groups, antidote and caller overlays on the immutable published base."""
import io,json,os,sys,unittest,collections
from pathlib import Path
from unittest.mock import patch
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as exporter

class WorldNorthExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/golden-world-north-content.json').read_text(encoding='utf-8'))
        cls.path=Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK',str(ci.ROOT/'artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')))
        cls.base=ci.content(cls.path,cls.pin['iteration']['base']);cls.proof=cls.pin['iteration']['provenance']
        cls.evidence=json.loads((ci.ROOT/cls.proof).read_text(encoding='utf-8'))
        cls.result=exporter.export_from_base(cls.base,cls.proof,cls.pin)
    def test_repeatable_complete_manifest_and_unchanged_reviewed_media(self):
        self.assertEqual(self.result,exporter.export_from_base(self.base,self.proof,self.pin))
        for name,raw in self.base.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        for name,expected in json.loads(self.result['manifest.json'])['files'].items():self.assertEqual(expected,ci.sha(self.result[name]))
    def test_north_has_all_original_groups_and_original_enemy_graphics(self):
        combat=json.loads(self.result['combat.json']);zone=next(z for z in combat['zones']if z['id']==4)
        self.assertEqual(14,len(zone['groups']));self.assertEqual({10,11},{e['enemyId']for g in zone['groups']for e in g['entities']})
        self.assertEqual([],json.loads(self.result['scene25.json'])['unavailableRegions'])
        for enemy in (10,11):
            self.assertGreater(len(Image.open(io.BytesIO(self.result[f'battle-enemy-{enemy}.png'])).getcolors(65536)),1)
        e=next(e for e in combat['enemies']if e['id']==10);self.assertEqual(7,e['behaviorByte']);self.assertEqual('UNKNOWN',e['nameConfidence'])
    def test_north_preparation_stays_in_actual_zone4_and_can_return_without_a_shortcut(self):
        m=json.loads(self.result['scene25.json']);world=json.loads(self.result['scene.json'])
        zones=json.loads(self.result['combat.json'])['zones'];zone=next(z for z in zones if z['mapId']==25 and z['id']==4)
        contains=lambda z,x,y:any(l<x<=r and t<y<=b for l,t,r,b in z['rectangles'])
        self.assertTrue(all(contains(zone,x,y)for x,y in [(12,21),(12,22)]))
        self.assertFalse(contains(zone,39,40));self.assertFalse(contains(zone,39,41))
        old_zone=next(z for z in zones if z['mapId']==25 and z['id']==1)
        self.assertTrue(contains(old_zone,39,40));self.assertTrue(contains(old_zone,39,41))
        exits={tuple(e['trigger'])for e in world['exits']if e['fromMapId']==25}
        allowed=set(m['enabledCells'])-set(m['dynamicObjectCells']);w=m['width']
        for start,target in [((38,43),(12,22)),((12,21),(12,22)),((12,22),(12,21)),((12,22),(39,42))]:
            queue=collections.deque([start]);seen={start}
            while queue:
                x,y=queue.popleft()
                if(x,y)==target:break
                for p in [(x,y-1),(x,y+1),(x-1,y),(x+1,y)]:
                    nx,ny=p
                    if 0<=nx<w and 0<=ny<m['height'] and ny*w+nx in allowed and(p not in exits or p==target)and p not in seen:
                        seen.add(p);queue.append(p)
            self.assertIn(target,seen,(start,target))
        source=(ci.ROOT/'android/app/src/androidTest/java/org/fengshen/dev/TouchTest.kt').read_text(encoding='utf-8')
        driver=source.split('fun testNormalWorldNorthPalaceAndPearlFromVerifiedNanhaiSave()',1)[1].split('fun testWorldNorthPalacePearlColdStartMatchesNormalSave()',1)[0]
        self.assertIn('walkTo(12,22)',driver);self.assertIn('if(y>21)Key.UP else Key.DOWN',driver)
        self.assertIn('it.mapId==25&&it.rectangles==listOf(EncounterRect(2,0,30,22),EncounterRect(31,0,63,35))&&it.contains',driver)
    def test_true_northwest_records_caller_and_exact_antidote_definition(self):
        scene=json.loads(self.result['scene.json']);edges={(e['fromMapId'],tuple(e['trigger']),e['toMapId'],tuple(e['spawn']))for e in scene['exits']}
        self.assertIn((25,(26,14),16,(186,102)),edges);self.assertIn((16,(186,102),25,(26,14)),edges)
        self.assertTrue(all(e.get('captureCaller')for e in scene['exits']if e['fromMapId']==0 and e['toMapId']in (17,18,19,22)))
        self.assertTrue(all(e.get('returnToCaller')for e in scene['exits']if e['fromMapId']in (17,18,19,22)))
        bull=next(i for i in scene['items']if i['id']=='rom.medicine.6')
        self.assertEqual({'mapMenu':True,'cureStatusMask':2,'confirmationConsumption':1,'extraConsumptionWhenCured':1,'evidence':'game-data/provenance/world-status.json'},bull['antidoteUse'])
    def test_existing_boss_growth_stores_herb_and_media_rules_are_preserved(self):
        a=json.loads(self.base['scene.json']);b=json.loads(self.result['scene.json'])
        for k in ('initialPlayer','initialMoney','shops','inns','nanhai','dialogues','npcs','maps'):self.assertEqual(a[k],b[k],k)
        self.assertEqual([i for i in a['items']if i['id']!='rom.medicine.6'],[i for i in b['items']if i['id']!='rom.medicine.6'])
        a=json.loads(self.base['combat.json']);b=json.loads(self.result['combat.json'])
        for k in ('bosses','nezhaGrowth','groups','zone','physicalRules','defeat','gate'):self.assertEqual(a[k],b[k],k)
        self.assertEqual(a['enemies'],[e for e in b['enemies']if e['id']not in (10,11)])
    def test_bad_effect_exit_members_enemy_pixel_or_capability_gate_rejected(self):
        for kind in ('effect','exit','group','enemy','pixel','capability','caller'):
            e=json.loads(json.dumps(self.evidence))
            if kind=='effect':e['itemUpdates'][0]['antidoteUse']['extraConsumptionWhenCured']=0
            elif kind=='exit':e['exits'][0]['spawn'][0]+=1
            elif kind=='group':e['combatOverlay']['zones'][0]['groups'].pop()
            elif kind=='enemy':e['combatOverlay']['enemies'][0]['hp']-=1
            elif kind=='pixel':e['graphics']['battle-enemy-10.png']['rgbaSha256']='0'*64
            elif kind=='capability':e['sceneCapabilityUpdates'][0]['implementedCapabilities'].pop()
            else:e['exitContextUpdates'][0]['trigger'][0]+=1
            def read(path):return e if Path(path).resolve()==(ci.ROOT/self.proof).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(exporter,'load',side_effect=read):
                with self.assertRaises(ValueError,msg=kind):exporter.export_from_base(self.base,self.proof,self.pin)

if __name__=='__main__':unittest.main()
