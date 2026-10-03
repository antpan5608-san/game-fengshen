"""Actual contact/four-floor data, original groups and shared chest path."""
import copy,collections,json,os,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import ci_apk as ci
import export_development as ex
from forensics.fengshen246 import extract_encounter_groups

class Tree107ExportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pin=json.loads((ci.ROOT/'ci/content-source.json').read_text(encoding='utf-8'));cls.path='game-data/provenance/world-tree107-content.json'
        cls.p=json.loads((ci.ROOT/cls.path).read_text(encoding='utf-8'));cls.parent=json.loads((ci.ROOT/cls.p['baseExport']['pinPath']).read_text(encoding='utf-8'))
        cls.base=ci.content(Path(os.environ.get('FENGSHEN_CONTENT_BASE_APK','/workspace/game-fengshen/artifacts/world-full01/f0-candidate/fengshen-remake-v27-release.apk')),cls.pin['iteration']['base'])
        cls.old=ex.export_from_base(cls.base,cls.parent['iteration']['provenance'],cls.parent);cls.result=ex.export_from_base(cls.base,cls.path,cls.pin);cls.r=ex.iteration_reader()
    def changed(self,p):
        def load(path):return p if Path(path).resolve()==(ci.ROOT/self.path).resolve()else json.loads(Path(path).read_text(encoding='utf-8'))
        with patch.object(ex,'load',side_effect=load):return ex.export_from_base(self.base,self.path,self.pin,verify_target=False)
    def test_exact_maps_original_complete_encounters_and_unchanged_media(self):
        self.assertEqual(self.result,ex.export_from_base(self.base,self.path,self.pin));self.assertEqual(211,len(self.result))
        self.assertEqual(self.pin['manifestSha256'],ex.digest(self.result['manifest.json']))
        for name,raw in self.old.items():
            if not name.endswith('.json'):self.assertEqual(raw,self.result[name],name)
        s=json.loads(self.result['scene.json']);self.assertEqual(38,len(s['maps']))
        self.assertEqual({107,108,109,110},{m['id']for m in s['maps']}-{m['id']for m in json.loads(self.old['scene.json'])['maps']})
        c=json.loads(self.result['combat.json']);self.assertEqual({40,41,42},{e['id']for e in c['enemies']}-{e['id']for e in json.loads(self.old['combat.json'])['enemies']})
        for mid,zone in ((16,18),(107,19),(108,19),(109,19),(110,19)):
            z=next(x for x in c['zones']if x['id']==zone and x['mapId']==mid)
            self.assertEqual(extract_encounter_groups(self.r,zone)['groups'],z['groups'])
            self.assertEqual(('LOW',16)if zone==18 else('HIGH',245),(z['randomGate'],z['randomThreshold']))
        ci.validate_item_sources(self.result)
    def test_contact_keeps_real_barriers_presence_and_asymmetric_return(self):
        s=json.loads(self.result['scene.json']);old=json.loads(self.old['scene.json'])
        self.assertEqual(old['sceneBarriers'],s['sceneBarriers']);self.assertEqual(old['mapObjects'],s['mapObjects'])
        contacts=[e for e in s['exits']if e.get('triggerMode')=='ACTOR_CONTACT'];self.assertEqual(2,len(contacts))
        self.assertEqual([(231,[170,148]),(232,[170,149])],[(e['contactActorId'],e['trigger'])for e in contacts])
        for e in contacts:
            self.assertEqual((16,107,[7,14]),(e['fromMapId'],e['toMapId'],e['spawn']))
            self.assertTrue(e['preserveArrivalDirection']);self.assertTrue(e['resetEncounterSteps'])
        returned=next(e for e in s['exits']if e['fromMapId']==107 and e['toMapId']==16)
        self.assertEqual(([7,14],[169,149]),(returned['trigger'],returned['spawn']))
        self.assertFalse(any(e['fromMapId']==16 and e['toMapId']==107 and e.get('triggerMode')!='ACTOR_CONTACT'for e in s['exits']))
    def test_original_floor_graph_reaches_yang_and_all_three_chests_without_teleport(self):
        s=json.loads(self.result['scene.json']);maps={mid:json.loads(self.result[f'scene{mid}.json'])for mid in(107,108,109,110)}
        exits={(e['fromMapId'],*e['trigger']):(e['toMapId'],*e['spawn'])for e in s['exits']if e['fromMapId']in maps}
        start=(107,7,14);queue=collections.deque([start]);seen={start}
        while queue:
            mid,x,y=queue.popleft();m=maps[mid]
            for dx,dy in((0,-1),(0,1),(-1,0),(1,0)):
                tx,ty=x+dx,y+dy;idx=ty*m['width']+tx
                if not(0<=tx<m['width']and 0<=ty<m['height'])or idx not in m['enabledCells']or idx in m['dynamicObjectCells']:continue
                target=exits.get((mid,tx,ty),(mid,tx,ty))
                if target[0]in maps and target not in seen:seen.add(target);queue.append(target)
        self.assertIn((110,7,6),seen)
        for npc in s['npcs']:
            if npc['mapId']not in maps:continue
            mid=npc['mapId'];x,y=npc['cell'];self.assertTrue(any((mid,x+dx,y+dy)in seen for dx,dy in((0,-1),(0,1),(-1,0),(1,0))),npc['id'])
        for mid,m in maps.items():
            self.assertEqual([0],m['walkableClasses']);self.assertEqual({0,1},set(m['collision']))
            self.assertFalse(any(i in m['enabledCells']for i,c in enumerate(m['collision'])if c==1),mid)
    def test_guessed_contact_walls_rewards_and_nerfed_or_partial_groups_reject(self):
        for kind in('contact','wall','reward','group','hp','talk'):
            p=copy.deepcopy(self.p)
            if kind=='contact':next(e for e in p['exits']if e['kind']=='ACTOR_CONTACT')['spawn'][0]=8
            elif kind=='wall':p['maps'][0]['walkableClasses'].append(1)
            elif kind=='reward':p['npcs'][0]['treasure']['itemId']='rom.armor.0'
            elif kind=='group':p['combatOverlay']['zones'][0]['groups'].pop()
            elif kind=='hp':p['combatOverlay']['enemies'][0]['hp']=1
            else:p['npcs'][-1]['originalTalk']['witnessFlagId']='invented.party.join'
            with self.assertRaises(ValueError,msg=kind):self.changed(p)

if __name__=='__main__':unittest.main()
