import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMMIT = 'd636453f14f86a096f9d293bf3facfd96cfcb614'
SOURCES = ['ORIGINAL_ROM','REFERENCE_PROJECT','GAMEPLAY_VERIFIED','GUIDE','VIDEO','MANUAL','INFERRED','UNKNOWN']
CONFIDENCES = ['VERIFIED','HIGH','MEDIUM','LOW','UNKNOWN']
DOMAINS = ['maps','dialogues','enemies','progression','items','equipment','skills','npcs','shops','chests','events','assets']
DIFFS = ['MATCH','LIKELY_MATCH','MODIFIED','REFERENCE_ONLY','ROM_ONLY','UNKNOWN']

def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))

def save(path, data):
    p=Path(path);p.parent.mkdir(parents=True,exist_ok=True)
    encoded=json.dumps(data,ensure_ascii=False,sort_keys=True,indent=2,allow_nan=False)+'\n'
    temp=p.with_suffix(p.suffix+'.tmp');temp.write_text(encoded,encoding='utf-8');temp.replace(p)

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def contained(base, relative):
    base=Path(base).resolve();p=(base/relative).resolve()
    if not p.is_relative_to(base):raise ValueError('Path escapes input root: '+str(relative))
    return p

def provenance(table, row_id, file_hash):
    return {'id':f'source.reference.{table.lower()}.{row_id}', 'source':'REFERENCE_PROJECT',
      'confidence':'LOW','originalVerified':False,'licenseStatus':'UNKNOWN',
      'locator':{'repository':'https://github.com/v5100v5100/FengShenBang','commit':COMMIT,
       'path':'Resources/res/MainData','sha256':file_hash,'table':table,'primaryKey':row_id},
      'evidenceRefs':[], 'note':'Exact reference extraction; originality unverified.'}
