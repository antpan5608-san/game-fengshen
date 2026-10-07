"""Synthetic artifact rejection tests; no emulator/phone/normal-play claims."""
import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw
from tools import room28_evidence as room


class Room28EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.save = dict(contentVersion='opening-segment-001-c62', mapId=28, x=104, y=120,
            interiorContext=dict(callerMapId=0, returnX=12, returnY=23),
            flags={'rom.map.28.flag.1':True}, inventory={'rom.medicine.0':1},
            characters=[dict(id='nezha')], money=123, encounterSteps=17)
        self.index = dict(kind='NORMAL_NEW_GAME_GAMEVIEW_TOUCH_INPUTS', stateGrants=False,
            restoredFixture=False, expectedSave=copy.deepcopy(self.save))
        self.boundary = dict(kind='ACTUAL_APP_EXTERNAL_COLD_BOUNDARY', equal=True,
            differentTopLevelFields=[], before=copy.deepcopy(self.save), after=copy.deepcopy(self.save))
        names = ('world-room28-normal-00.mp4', 'world-room28-cold-restart.mp4')
        segments = []
        for name in names:
            # Deliberately not playable video. Test fixture checks transport integrity only.
            raw = ('SYNTHETIC_TRANSPORT_FIXTURE_ONLY:' + name).encode()
            (self.folder/name).write_bytes(raw)
            segments.append(dict(file='artifacts/checkpoint-ui/'+name,sha256=hashlib.sha256(raw).hexdigest()))
        segments[0]['savedWorldAfter'] = copy.deepcopy(self.save)
        segments[1]['savedWorldBefore'] = copy.deepcopy(self.save)
        self.recording = dict(source='Actual Android App screenrecord; SILENT, no sound validation',
            normalAssertions='PASS',forceStopRestartEqual=True,continuedExploration=True,
            originalPreferencesRestored=True,segments=segments,
            videos=['artifacts/checkpoint-ui/'+name for name in names])
        for stage in room.SCREENSHOTS:
            image = Image.new('RGB',(960,540),'black')
            ImageDraw.Draw(image).rectangle((5,5,40,40),fill='white')
            image.save(self.folder/('touch-ux-world-room28-'+stage+'.png'))
        self.write()

    def write(self):
        for name,value in [('world-room28-recording.json',self.recording),
            ('world-room28-cold-boundary.json',self.boundary),
            ('touch-ux-world-room28-normal-index.json',self.index),
            ('touch-ux-world-room28-expected-save.json',self.save)]:
            (self.folder/name).write_text(json.dumps(value),encoding='utf-8')

    def replace_all_saved_states(self, save):
        self.save=copy.deepcopy(save)
        self.index['expectedSave']=copy.deepcopy(save)
        self.boundary['before']=copy.deepcopy(save); self.boundary['after']=copy.deepcopy(save)
        self.recording['segments'][0]['savedWorldAfter']=copy.deepcopy(save)
        self.recording['segments'][1]['savedWorldBefore']=copy.deepcopy(save)
        self.write()

    def test_entire_state_and_normal_flow_metadata_cannot_be_weakened(self):
        self.assertEqual(set(room.PROOF_KEYS),set(room.proof_digests(self.folder)))
        originals=copy.deepcopy((self.recording,self.boundary,self.index,self.save))
        for mutation in ('cold','afterMoney','afterInventory','afterEncounter','afterActor',
            'differentFields','normal','grants','fixture','preferences','segmentState','controlled'):
            self.recording,self.boundary,self.index,self.save=copy.deepcopy(originals)
            if mutation=='cold':self.boundary['equal']=False
            elif mutation=='afterMoney':self.boundary['after']['money']+=1
            elif mutation=='afterInventory':self.boundary['after']['inventory']['rom.medicine.0']=2
            elif mutation=='afterEncounter':self.boundary['after']['encounterSteps']+=1
            elif mutation=='afterActor':self.boundary['after']['characters'][0]['id']='yangjian'
            elif mutation=='differentFields':self.boundary['differentTopLevelFields']=['money']
            elif mutation=='normal':self.recording['normalAssertions']='NOT_RUN'
            elif mutation=='grants':self.index['stateGrants']=True
            elif mutation=='fixture':self.index['restoredFixture']=True
            elif mutation=='preferences':self.recording['originalPreferencesRestored']=False
            elif mutation=='segmentState':self.recording['segments'][1]['savedWorldBefore']['money']+=1
            else:self.recording['controlledAssertions']='PASS'
            self.write()
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):room.proof_digests(self.folder)

    def test_self_consistent_wrong_endpoint_and_boolean_quantity_are_rejected(self):
        original=copy.deepcopy(self.save)
        for mutation in ('version','map','cell','caller','return','medicine','booleanMedicine','booleanCaller','flag','party'):
            save=copy.deepcopy(original)
            if mutation=='version':save['contentVersion']='opening-segment-001-c61'
            elif mutation=='map':save['mapId']=0
            elif mutation=='cell':save['x']=120
            elif mutation=='caller':save['interiorContext']['callerMapId']=1
            elif mutation=='return':save['interiorContext']['returnY']=24
            elif mutation=='medicine':save['inventory']['rom.medicine.0']=2
            elif mutation=='booleanMedicine':save['inventory']['rom.medicine.0']=True
            elif mutation=='booleanCaller':save['interiorContext']['callerMapId']=False
            elif mutation=='flag':save['flags']['rom.map.28.flag.1']=False
            else:save['characters'].append(dict(id='xiaolongnv'))
            self.replace_all_saved_states(save)
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):room.proof_digests(self.folder)

    def test_missing_changed_video_and_blank_or_wrong_size_image_are_rejected(self):
        video=self.folder/'world-room28-normal-00.mp4';raw=video.read_bytes()
        video.write_bytes(raw+b'changed')
        with self.assertRaises(ValueError):room.proof_digests(self.folder)
        video.write_bytes(raw)
        screenshot=self.folder/('touch-ux-world-room28-'+room.SCREENSHOTS[-1]+'.png')
        raw=screenshot.read_bytes()
        for size in ((960,540),(2640,1216)):
            Image.new('RGB',size,'black').save(screenshot)
            with self.subTest(size=size),self.assertRaises(ValueError):room.proof_digests(self.folder)
        screenshot.write_bytes(raw);self.assertEqual(3,len(room.proof_digests(self.folder)))
        screenshot.unlink()
        with self.assertRaises(ValueError):room.proof_digests(self.folder)


if __name__=='__main__':unittest.main()
