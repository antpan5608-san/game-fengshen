"""Original final hall and durable rebirth schema; not normal App acceptance."""
import copy,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_map,extract_encounter_groups

class FinalHallExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'))
        cls.path=cls.pin['iteration']['provenance'];cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.r=ex.iteration_reader();cls.result=ex.export_from_base(cls.base,cls.path,cls.pin)
        cls.scene=json.loads(cls.result['scene.json']);cls.combat=json.loads(cls.result['combat.json'])
    def test_original_final_boss_gate_scene_geometry_and_real_completion(self):
        for mid in [68,86]:
            data=json.loads(self.result[f'scene{mid}.json']);original=extract_map(self.r,mid)
            self.assertEqual([t for row in original['grid']for t in row],data['grid'])
        z=next(z for z in self.combat['zones']if z['mapId']==68)
        self.assertEqual(extract_encounter_groups(self.r,15)['groups'],z['groups'])
        self.assertFalse(any(z['mapId']==86 for z in self.combat['zones']))
        enemy=next(e for e in self.combat['enemies']if e['id']==151)
        self.assertEqual((3500,8,750,820),(enemy['hp'],enemy['behaviorByte'],enemy['experienceReward'],enemy['moneyReward']))
        story=self.scene['sceneStories'][0];proof=ex.validate_world_rebirth_script(self.r,story)
        self.assertEqual(56,proof['cpuCaseCount']);self.assertEqual(list(range(2,13)),[int(i.rsplit('.',1)[1])for i in story['continuation']['dialogueIds']])
        self.assertEqual((16,238,160),tuple(story['continuation']['destination'][k]for k in ['mapId','x','y']))
        self.assertNotIn('joinCharacterId',story['continuation'])
        rows=[e for e in self.scene['exits']if e['fromMapId']==68 and e['toMapId']==86]
        self.assertEqual(1,len(rows));self.assertEqual([12,1],rows[0]['trigger']);self.assertEqual([12,5],rows[0]['spawn'])
        self.assertFalse(any(e['fromMapId']==86 and e['trigger']==[19,13]for e in self.scene['exits']))
        self.assertFalse(any(e['fromMapId']==86 and e['trigger']==[8,13]for e in self.scene['exits']))
        self.assertFalse(any(e['fromMapId']==23 and e['trigger']==[12,5]and e['toMapId']==68 for e in self.scene['exits']))
        walls=[r for r in self.p['evidence']['unresolvedExitRows']if r['status']=='UNRESOLVED_SOURCE_PHYSICAL_WALL_NOT_ENABLED']
        self.assertEqual(2,len(walls));self.assertTrue(all(r['originalCollisionClass']==1 for r in walls))
        ex.validate_world_exit_geometry(self.scene,self.result)
        ex.validate_world_hall_batch_terrain(self.r,86,'game-data/provenance/world-rebirth-terrain.json')
    def test_repeatable_export_preserves_all_previous_media_and_strict_target_pin(self):
        pin=json.loads((ci.ROOT/'ci/golden-world-seventh-side-content.json').read_text(encoding='utf-8'))
        old=ex.export_from_base(self.base,pin['iteration']['provenance'],pin)
        for name,raw in old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin))
        self.assertEqual(180,len(self.result));self.assertEqual(31,len(self.scene['maps']))
        self.assertEqual(self.pin['manifestSha256'],ci.sha(self.result['manifest.json']))
    def test_guessed_destination_free_poison_steps_rewards_and_npc_pose_rejected(self):
        for case in ['destination','steps','reward','pose','boss']:
            p=copy.deepcopy(self.p);story=p['sceneStories'][0]
            if case=='destination':story['continuation']['destination']['x']=243
            elif case=='steps':story['openingMovement']['completedSteps']=0
            elif case=='reward':story['continuation']['joinCharacterId']='xiaolongnv'
            elif case=='pose':p['graphics']['npc-132-rebirth.png']['rgbaSha256']='0'*64
            else:next(e for e in p['combatOverlay']['enemies']if e['id']==151)['hp']=1
            def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=load),self.assertRaises(ValueError,msg=case):ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_real_exit_record_on_physical_wall_is_rejected_before_signing(self):
        for mid in [23,86]:
            p=copy.deepcopy(self.p)
            row=next(r for r in p['evidence']['unresolvedExitRows']if r['fromMapId']==mid and r['status']=='UNRESOLVED_SOURCE_PHYSICAL_WALL_NOT_ENABLED')
            p['exits'].append(row)
            def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
            with patch.object(ex,'load',side_effect=load),self.assertRaisesRegex(ValueError,'Invalid exit geometry'):
                ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_export_geometry_uses_existing_left_top_exclusive_region_boundaries(self):
        data={'width':3,'height':3,'collision':[0]*9,'walkableClasses':[0],
              'enabledCells':list(range(9)),'unavailableRegions':[[0,0,2,2]]}
        scene={'maps':[{'id':1,'scene':'scene1.json'}],'exits':[]}
        result={'scene1.json':json.dumps(data).encode()}
        for cell in [[0,1],[1,0]]:
            scene['exits']=[{'fromMapId':1,'trigger':cell,'toMapId':1,'spawn':cell}]
            ex.validate_world_exit_geometry(scene,result)
        for cell in [[1,1],[2,2]]:
            scene['exits']=[{'fromMapId':1,'trigger':cell,'toMapId':1,'spawn':cell}]
            with self.assertRaisesRegex(ValueError,'Invalid exit geometry'):
                ex.validate_world_exit_geometry(scene,result)

if __name__=='__main__':unittest.main()
