"""Select the original build/runtime artifacts by successful job time, never artifact-ID order."""
import argparse
from datetime import datetime
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tempfile
import zipfile

REPOSITORY='antpan5608-san/game-fengshen'
LIMIT=256*1024*1024


def instant(value):
    try:
        result=datetime.fromisoformat(value.replace('Z','+00:00'))
        if result.tzinfo is None:raise ValueError()
        return result
    except (ValueError,TypeError,AttributeError):
        raise ValueError('Invalid artifact/job timestamp') from None


def select(run, listing, run_id, source, runtime_job):
    if (runtime_job not in ('runtime','runtime-continuation') or not re.fullmatch('[a-f0-9]{40}',source)
            or run.get('status')!='completed' or run.get('conclusion')!='success'
            or run.get('headSha')!=source or run.get('headBranch')!='main'
            or run.get('event')!='workflow_dispatch' or run.get('workflowName')!='Android cloud build'):
        raise ValueError('Artifact selection requires the original successful reviewed main build')
    artifacts=listing.get('artifacts',[])
    if listing.get('total_count')!=len(artifacts):raise ValueError('Incomplete artifact listing')
    selected={}
    for key,name,job_name in [('signed','fengshen-signed-apk','build'),
            ('runtime','fengshen-town02-runtime-evidence',runtime_job)]:
        jobs=[j for j in run.get('jobs',[]) if j.get('name')==job_name]
        if len(jobs)!=1 or jobs[0].get('status')!='completed' or jobs[0].get('conclusion')!='success':
            raise ValueError('Missing unique successful artifact-producing job')
        start,end=instant(jobs[0].get('startedAt')),instant(jobs[0].get('completedAt'))
        if end<start:raise ValueError('Invalid successful job time interval')
        candidates=[]
        for artifact in artifacts:
            if artifact.get('name')!=name:continue
            created=instant(artifact.get('created_at'))
            origin=artifact.get('workflow_run',{})
            if (start<=created<=end and artifact.get('expired') is False
                    and str(origin.get('id'))==str(run_id) and origin.get('head_sha')==source
                    and origin.get('head_branch')=='main'):
                candidates.append(artifact)
        if len(candidates)!=1:raise ValueError('Missing or ambiguous artifact from the successful job')
        artifact=candidates[0]
        if (type(artifact.get('id')) is not int or artifact['id']<=0
                or type(artifact.get('size_in_bytes')) is not int or not 0<artifact['size_in_bytes']<=LIMIT
                or not re.fullmatch(r'sha256:[a-f0-9]{64}',artifact.get('digest',''))):
            raise ValueError('Invalid immutable artifact identity/digest/size')
        selected[key]={k:artifact[k]for k in ('id','name','digest','size_in_bytes','created_at')}
    return selected


def extract_verified(raw, artifact, destination):
    if len(raw)>LIMIT or len(raw)!=artifact['size_in_bytes'] or 'sha256:'+hashlib.sha256(raw).hexdigest()!=artifact['digest']:
        raise ValueError('Artifact ZIP bytes differ from the selected immutable digest')
    destination=Path(destination)
    if destination.exists() and any(destination.iterdir()):raise ValueError('Refusing to overwrite review evidence')
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries=archive.infolist();names=[e.filename for e in entries]
        if len(names)!=len(set(names)) or len(names)>5000 or sum(e.file_size for e in entries)>512*1024*1024:
            raise ValueError('Duplicate or excessive artifact ZIP entries')
        for entry in entries:
            # Windows normalizes separators while reading ZipInfo.filename;
            # validate original archive bytes before allowing that conversion.
            name=entry.orig_filename;path=PurePosixPath(name)
            if (name!=entry.filename or not path.parts or name.rstrip('/')!=str(path) or path.is_absolute() or '..' in path.parts or '\\' in name
                    or ':' in name or (entry.external_attr>>16)&0o170000==0o120000):
                raise ValueError('Unsafe artifact ZIP path')
        destination.mkdir(parents=True,exist_ok=True)
        archive.extractall(destination)


def download(artifact,destination):
    # gh owns authentication; tokens and redirect URLs never enter arguments or output.
    with tempfile.TemporaryFile() as stream:
        result=subprocess.run(['gh','api',f"repos/{REPOSITORY}/actions/artifacts/{artifact['id']}/zip"],
            stdout=stream,stderr=subprocess.PIPE,timeout=180)
        if result.returncode:raise ValueError('Selected artifact download failed')
        stream.seek(0);raw=stream.read(LIMIT+1)
    extract_verified(raw,artifact,destination)


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--run',type=Path,required=True)
    parser.add_argument('--artifacts',type=Path,required=True);parser.add_argument('--run-id',required=True)
    parser.add_argument('--source',required=True);parser.add_argument('--runtime-job',required=True)
    parser.add_argument('--download',action='store_true');args=parser.parse_args()
    if not re.fullmatch('[0-9]+',args.run_id):raise ValueError('Invalid reviewed build run ID')
    selected=select(json.loads(args.run.read_text(encoding='utf-8-sig')),
        json.loads(args.artifacts.read_text(encoding='utf-8-sig')),args.run_id,args.source,args.runtime_job)
    if args.download:
        download(selected['signed'],'artifacts/ci');download(selected['runtime'],'artifacts/runtime-review')
    print(json.dumps(selected,sort_keys=True))


if __name__=='__main__':main()
