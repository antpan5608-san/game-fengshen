"""Bind new original art/decoder and normal item/battle/cold evidence to the existing CI receipt."""
import hashlib
import json
import re
from pathlib import Path

GATES=['battleVisualAssets','battleVisualNormalAndCold']
PROOF_KEYS=('battleVisualEvidenceSha256',)
ACCEPTANCE=dict(kind='ORIGINAL_ART_C62_NORMAL_SUPPLY_AND_CONTROLLED_PARTY_NOT_PHONE',
    manifestSha256='4480914b805e4b5c99feeaf9d207227a60bca4accf3379fd151c13ccbaac571c',
    assets=12,normalMethod='testNormalVisualSupplyAttackVictoryAndSave',coldMethod='testHerbColdStartMatchesNormalSave',
    decoderMethod='testBattleVisualAssetsHashesCacheAndReadOnlySnapshots',proofKey=PROOF_KEYS[0])


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
    return {PROOF_KEYS[0]:hashlib.sha256(json.dumps(hashes,sort_keys=True,separators=(',',':')).encode()).hexdigest()}
