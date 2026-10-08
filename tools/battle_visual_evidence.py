"""Bind new original art/decoder and normal item/battle/cold evidence to the existing CI receipt."""
import hashlib
import json
import math
import re
from pathlib import Path

GATES=['battleVisualAssets','battleVisualNormalAndCold','battleVisualControlledPosesReadOnly']
PROOF_KEYS=('battleVisualEvidenceSha256',)
ACCEPTANCE=dict(kind='ORIGINAL_ART_C62_NORMAL_SUPPLY_AND_CONTROLLED_PARTY_NOT_PHONE',
    manifestSha256='8dc53b77027055a2b9a2ec37a80e2aba3113e668bd350ab822c14c3d85794f30',
    assets=16,controlledPoseMethod='testControlledBattleVisualPosesReadOnly',fonts=[1.0,1.3,2.0],normalMethod='testNormalVisualSupplyAttackVictoryAndSave',coldMethod='testHerbColdStartMatchesNormalSave',
    decoderMethod='testBattleVisualAssetsHashesCacheAndReadOnlySnapshots',proofKey=PROOF_KEYS[0])
ACCEPTANCE['supportFeedback']='IDENTIFIED_ORIGINAL_HEAL_ANTIDOTE_REAL_PARTY_TARGET_LOCAL_GLOW'
ACCEPTANCE['preparation']='PORTRAITS_STARTUP_SCOPED_BATTLE_EPOCH_GUARDS'
ACCEPTANCE['partySpacing']='SEPARATED_CROPS_BOUNDED_ATTACK'
ACCEPTANCE['enemyFeedback']='ACTUAL_SPRITE_MEASURED_SHORT_FEEDBACK_V1'
POSE_FILES=tuple(sorted(('nezha-portrait-v1.png','xiaolongnv-portrait-v1.png',
    'yangjian-portrait-v2.png','jiangziya-portrait-v1.png','nezha-idle-v1.png',
    'xiaolongnv-idle-v1.png','yangjian-idle-v2.png','jiangziya-idle-v1.png',
    'nezha-attack.png','xiaolongnv-cast.png','enemy-1.png','grass-v1.png')))
DELIVERY_GUARDS=('staleEpochRejected','otherBattleRejected','exitRejected','destroyedOwnerRejected')


def verify_party_bodies(phase,width,height):
    bodies=phase.get('partyBodies',[])
    actors=('nezha','xiaolongnv','yangjian','jiangziya')
    files=['nezha-idle-v1.png','xiaolongnv-idle-v1.png','yangjian-idle-v2.png','jiangziya-idle-v1.png']
    if phase.get('kind')=='ATTACK':files[0]='nezha-attack.png'
    if phase.get('kind') in ('SPECIAL','HEAL'):files[1]='xiaolongnv-cast.png'
    if not isinstance(bodies,list) or len(bodies)!=4:raise ValueError('Missing actual party crop positions')
    for row,actor,file in zip(bodies,actors,files):
        if not isinstance(row,dict) or row.get('actor')!=actor or row.get('file')!=file:
            raise ValueError('Party crop identity differs from the original action')
        if any(type(row.get(k)) not in (int,float) or not math.isfinite(row[k]) for k in ('x','y','w','h')):
            raise ValueError('Invalid actual party crop position')
        x,y,w,h=(row[k]for k in ('x','y','w','h'))
        if x<0 or y<0 or w<=0 or h<=0 or x+w>width+.01 or y+h>height+.01:
            raise ValueError('Party crop lies outside the actual screen')
    for i,a in enumerate(bodies):
        for b in bodies[i+1:]:
            if (min(a['x']+a['w'],b['x']+b['w'])-max(a['x'],b['x'])>.01
                    and min(a['y']+a['h'],b['y']+b['h'])-max(a['y'],b['y'])>.01):
                raise ValueError('Actual party crops overlap')


def bounded(path,limit):
    if path.is_symlink() or not path.is_file() or not 0<path.stat().st_size<=limit:
        raise ValueError('Missing or unsafe visual proof: '+path.name)
    return path.read_bytes()


def validate_digests(receipt):
    for key in PROOF_KEYS:
        if not isinstance(receipt.get(key),str) or not re.fullmatch('[a-f0-9]{64}',receipt[key]):
            raise ValueError('Missing visual proof digest')


def proof_digests(evidence,logs):
    evidence,logs=Path(evidence),Path(logs);hashes={}
    names=('world-visual-normal-normal-test.txt','world-visual-normal-cold-start-test.txt')
    for directory,name in [(logs,'battle-visual-assets.txt'),*((evidence,n)for n in names)]:
        raw=bounded(directory/name,512*1024);text=raw.decode('utf-8')
        if not re.search(r'^OK \(1 test\)\s*$',text,re.M) or re.search('FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED',text):
            raise ValueError('Visual App test did not pass: '+name)
        hashes[name]=hashlib.sha256(raw).hexdigest()
    for name in ('world-visual-normal-recording.json','world-visual-normal-cold-boundary.json'):
        raw=bounded(evidence/name,1024*1024);hashes[name]=hashlib.sha256(raw).hexdigest()
        value=json.loads(raw)
        if name.endswith('recording.json'):
            recording=value
            if value.get('normalAssertions')!='PASS' or value.get('forceStopRestartEqual') is not True or value.get('continuedExploration') is not True or value.get('originalPreferencesRestored') is not True or value.get('controlledAssertions') is not None:
                raise ValueError('Visual normal recording cannot be a controlled fixture or omit cold/restore')
        else:
            boundary=value
            if value.get('kind')!='ACTUAL_APP_EXTERNAL_COLD_BOUNDARY' or value.get('equal') is not True or value.get('before')!=value.get('after') or value.get('differentTopLevelFields')!=[]:
                raise ValueError('Visual full cold state changed')
    segments=recording.get('segments',[])
    if not 2<=len(segments)<=8 or recording.get('videos')!=[s.get('file')for s in segments]:
        raise ValueError('Missing normal/cold original video segments')
    if segments[-1].get('phase')!='EXTERNAL_FORCE_STOP_ACTUAL_COLD_RESTART_AND_CONTINUE' or segments[-1].get('savedWorldBefore')!=boundary['before']:
        raise ValueError('Visual recorder/cold endpoint mismatch')
    for segment in segments:
        name=Path(segment['file']).name
        if segment['file']!='artifacts/checkpoint-ui/'+name or not re.fullmatch(r'world-visual-normal-(normal-\d\d|cold-restart)\.mp4',name):
            raise ValueError('Unsafe visual video path')
        raw=bounded(evidence/name,64*1024*1024);digest=hashlib.sha256(raw).hexdigest()
        if digest!=segment.get('sha256'):raise ValueError('Visual original video bytes changed')
        hashes[name]=digest
    from PIL import Image
    if __package__:
        from .battle_magic_evidence import complete_save
    else:
        from battle_magic_evidence import complete_save
    for font in ('1.0','1.3','2.0'):
        name='touch-ux-world-visual-poses-'+font+'.json'
        raw=bounded(evidence/name,1024*1024);value=json.loads(raw);hashes[name]=hashlib.sha256(raw).hexdigest()
        if (value.get('kind')!='CONTROLLED_REAL_ACTION_QUEUE_VISUAL_ONLY_NOT_NORMAL_JOIN_OR_PHONE'
                or value.get('font')!=float(font) or value.get('manifestSha256')!=ACCEPTANCE['manifestSha256']
                or value.get('prepared')!=len(POSE_FILES) or not 0<value.get('decodedBytes',0)<=64*1024*1024
                or value.get('renderStateUnchanged') is not True or value.get('rngUnchanged') is not True
                or value.get('before')!=value.get('after')):
            raise ValueError('Controlled pose report changed state, source, font or budget')
        if (value.get('startupPrepared')!=4 or value.get('preparedFiles')!=list(POSE_FILES)
                or any(value.get(key) is not True for key in DELIVERY_GUARDS)):
            raise ValueError('Scoped preparation or stale delivery guards were not verified')
        if (value.get('partySpacing')!=ACCEPTANCE['partySpacing']
                or type(value.get('projectedSpacingSamples')) is not int
                or not 36<=value['projectedSpacingSamples']<=1000):
            raise ValueError('Separated party crop projection was not verified')
        complete_save(value.get('before'))
        phases=value.get('phases',[])
        expected=[('xiaolongnv','SPECIAL','CAST','xiaolongnv-cast.png',44,5),
                  ('xiaolongnv','HEAL','CAST','xiaolongnv-cast.png',44,58),
                  ('xiaolongnv','TEXT','IDLE','xiaolongnv-idle-v1.png',41,58),
                  ('nezha','ATTACK','ATTACK','nezha-attack.png',41,58)]
        actual=[(p.get('actor'),p.get('kind'),p.get('pose'),p.get('file'),p.get('casterMP'),p.get('targetHP'))for p in phases]
        if actual!=expected:raise ValueError('Controlled original queue/pose timing differs')
        if (any('supportTarget' not in p or p.get('arenaFlash') is not False for p in phases)
                or [p.get('supportTarget')for p in phases]!=['nezha','nezha',None,None]):
            raise ValueError('Support feedback must stay local to the actual original target')
        for phase in phases:
            verify_party_bodies(phase,value.get('screenWidth',0),value.get('screenHeight',0))
            name=phase.get('screenshot','')
            if not re.fullmatch(r'touch-ux-world-visual-pose-'+font.replace('.','_')+r'-\d{1,2}\.png',name):
                raise ValueError('Unsafe controlled pose picture path')
            raw=bounded(evidence/name,10*1024*1024)
            with Image.open(evidence/name) as picture:
                if picture.size!=(value.get('screenWidth'),value.get('screenHeight')) or min(picture.size)<80 or picture.convert('RGB').getbbox() is None:
                    raise ValueError('Wrong or blank actual pose picture')
            hashes[name]=hashlib.sha256(raw).hexdigest()
        raw=bounded(logs/('testControlledBattleVisualPosesReadOnly-font-'+font+'.txt'),512*1024)
        text=raw.decode('utf-8')
        if not re.search(r'^OK \(1 test\)\s*$',text,re.M) or re.search('FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED',text):
            raise ValueError('Controlled pose App test did not pass')
        hashes['pose-test-'+font]=hashlib.sha256(raw).hexdigest()
    return {PROOF_KEYS[0]:hashlib.sha256(json.dumps(hashes,sort_keys=True,separators=(',',':')).encode()).hexdigest()}
