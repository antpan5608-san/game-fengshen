import copy
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
from forensics.common import DOMAINS
from forensics.comparison import empty_baseline
from forensics.validator import validate_canonical

SOURCE={'id':'source.test.fixture','source':'MANUAL','confidence':'UNKNOWN','originalVerified':False,'licenseStatus':'PERMITTED',
  'locator':{'path':'tests/test_validation.py'},'evidenceRefs':[],'note':'Synthetic validator fixture. Not game data.'}
SOURCES={'schemaVersion':1,'records':[SOURCE]}

def valid_fixture():
    d=empty_baseline();d.update(profile='synthetic-test',status='SYNTHETIC');e=d['entities']
    def add(domain,id,**props):e[domain].append({'id':id,'sourceRefs':['source.test.fixture'],**props})
    add('maps','test.map',name='TEST',width=4,height=3,assetIds=['test.asset'],transitions=[{'id':'test.exit','position':{'x':1,'y':2},'targetMapId':'test.map','targetPosition':{'x':0,'y':0}}])
    add('dialogues','test.dialogue',text='Synthetic',nextDialogueId=None,speakerNpcId='test.npc')
    add('npcs','test.npc',mapId='test.map',position={'x':1,'y':1},dialogueId='test.dialogue',eventId='test.event')
    add('enemies','test.enemy',name='Synthetic',hp=5,attack=2,defense=0,skillIds=['test.skill'])
    add('progression','test.level',characterId='test.character',level=1,hp=10,mp=0,experienceThreshold=0)
    add('items','test.item',name='Synthetic')
    add('equipment','test.equipment',itemId='test.item',slot='test')
    add('skills','test.skill',name='Synthetic',mpCost=0,characterId='test.character',learnLevel=1)
    add('shops','test.shop',mapId='test.map',npcId='test.npc',itemIds=['test.item'])
    add('chests','test.chest',mapId='test.map',position={'x':2,'y':1},itemId='test.item')
    add('events','test.event',mapId='test.map',actions=[{'type':'sequence','actions':[
      {'type':'dialogue','dialogueId':'test.dialogue'}, {'type':'giveItem','itemId':'test.item','quantity':1},
      {'type':'openShop','shopId':'test.shop'},{'type':'startBattle','enemyId':'test.enemy'},
      {'type':'useSkill','skillId':'test.skill'},{'type':'callEvent','eventId':'test.event'},
      {'type':'showNpc','npcId':'test.npc'},{'type':'chest','chestId':'test.chest'},
      {'type':'teleport','mapId':'test.map','position':{'x':0,'y':0}}]}])
    add('assets','test.asset',path='nonexistent.png',sha256='0'*64)
    return d

class ValidatorTests(unittest.TestCase):
    def check(self,d,code,root=None):self.assertIn(code,validate_canonical(d,SOURCES,root)['issuesByCode'])
    def test_synthetic_valid_contract(self):self.assertEqual(validate_canonical(valid_fixture(),SOURCES)['status'],'PASS')
    def test_every_required_typed_reference_and_nested_event(self):
        specs=[(0,'dialogueId','INVALID_DIALOGUE_REFERENCE'),(1,'itemId','INVALID_ITEM_REFERENCE'),
          (2,'shopId','INVALID_SHOP_REFERENCE'),(3,'enemyId','INVALID_ENEMY_REFERENCE'),
          (4,'skillId','INVALID_SKILL_REFERENCE'),(5,'eventId','INVALID_EVENT_REFERENCE'),
          (6,'npcId','INVALID_NPC_REFERENCE'),(8,'mapId','INVALID_MAP_REFERENCE')]
        for n,key,code in specs:
            with self.subTest(code=code):
                d=valid_fixture();d['entities']['events'][0]['actions'][0]['actions'][n][key]='test.missing';self.check(d,code)
    def test_duplicate_id(self):
        d=valid_fixture();d['entities']['items'].append(copy.deepcopy(d['entities']['items'][0]));self.check(d,'DUPLICATE_ID')
    def test_coordinates_boundary_and_transition(self):
        d=valid_fixture();d['entities']['npcs'][0]['position']['x']=4;self.check(d,'INVALID_COORDINATES')
        d=valid_fixture();d['entities']['maps'][0]['transitions'][0]['targetMapId']='test.missing';self.check(d,'INVALID_MAP_TRANSITION')
    def test_missing_asset_and_path_escape(self):
        with tempfile.TemporaryDirectory() as root:
            d=valid_fixture();self.check(d,'MISSING_ASSET',root);d['entities']['assets'][0]['path']='../outside.png';self.check(d,'MISSING_ASSET',root)
    def test_missing_dialogue_and_invalid_shape(self):
        d=valid_fixture();d['entities']['dialogues'][0]['text']=' ';self.check(d,'MISSING_DIALOGUE')
        d=valid_fixture();d['entities']['dialogues'][0]['text']='';self.check(d,'SCHEMA_VALIDATION')
        d=valid_fixture();d['entities']['items'][0]['unknownLegacyField']=123;self.check(d,'SCHEMA_VALIDATION')
    def test_impossible_learning_level_and_growth_gap(self):
        d=valid_fixture();d['entities']['skills'][0]['learnLevel']=2;self.check(d,'IMPOSSIBLE_PROGRESSION_REFERENCE')
        d=valid_fixture();row=copy.deepcopy(d['entities']['progression'][0]);row.update(id='test.level3',level=3);d['entities']['progression'].append(row);self.check(d,'IMPOSSIBLE_PROGRESSION_REFERENCE')
    def test_reference_cannot_become_canonical(self):
        d=valid_fixture();d.update(profile='verified-baseline',status='READY_FOR_REVIEW');self.check(d,'UNVERIFIED_CANONICAL_CONTENT')
    def test_false_verified_source_cannot_pass(self):
        s=copy.deepcopy(SOURCES);s['records'][0].update(originalVerified=True,confidence='VERIFIED',evidenceRefs=['source.test.fixture'])
        self.assertIn('UNSUPPORTED_ORIGINAL_VERIFICATION',validate_canonical(valid_fixture(),s)['issuesByCode'])
    def test_unknown_source_reference(self):
        d=valid_fixture();d['entities']['items'][0]['sourceRefs']=['source.missing'];self.check(d,'INVALID_PROVENANCE_REFERENCE')

if __name__=='__main__':unittest.main()
