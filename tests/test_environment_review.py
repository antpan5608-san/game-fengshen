"""Execute the workflow approval Bash against isolated APIs; Linux with bash/jq. No secrets."""
import os,json,subprocess,tempfile,copy
from pathlib import Path
workflow=Path('.github/workflows/android-publish.yml').read_text()
start=workflow.index('        run: |\n')+len('        run: |\n')
script='\n'.join(line[10:] for line in workflow[start:workflow.index('\n  publish:',start)].splitlines())
assert "inputs.mode == 'publish'" in workflow.split('  publish:',1)[1].split('  inspect:',1)[0]
inspection=workflow.split('  inspect:',1)[1]
assert 'environment: fengshen-production' in inspection and '-SummaryOnly' in inspection
assert all(x not in inspection for x in ['publish-apk.ps1','register-release.ps1','ALIYUN_ACCESS_KEY'])
build=dict(status='completed',conclusion='success',event='workflow_dispatch',head_branch='main',head_sha='fixture-commit',path='.github/workflows/android-build.yml')
pending=[dict(environment=dict(name='fengshen-production',id=123),current_user_can_approve=True)]
cases=[]
def case(name,modify=None,ok=False,token='fixture-token',run='123',digest='a'*64,targets=None,post_fail=False,operation='publish',build_job=True,runtime_job='success',world_job='success',continuation_job='success',quality='STABLE'):
    b=copy.deepcopy(build)
    if modify:b.update(modify)
    cases.append(dict(name=name,build=b,pending=copy.deepcopy(pending if targets is None else targets),ok=ok,token=token,run=run,digest=digest,post_fail=post_fail,operation=operation,build_job=build_job,runtime_job=runtime_job,world_job=world_job,continuation_job=continuation_job,quality=quality))
case('trusted main build approves exact production environment',ok=True)
case('missing token rejected',token='')
case('invalid build ID rejected',run='123;invalid')
case('invalid APK hash rejected',digest='deadbeef')
case('failed build rejected',{'conclusion':'failure'})
case('unfinished build rejected',{'status':'in_progress'})
case('push event rejected',{'event':'push'})
case('different commit rejected',{'head_sha':'other-commit'})
case('different branch rejected',{'head_branch':'feature'})
case('different workflow rejected',{'path':'.github/workflows/untrusted.yml'})
case('ineligible reviewer rejected',targets=[dict(environment=dict(name='fengshen-production',id=123),current_user_can_approve=False)])
case('ambiguous production environments rejected',targets=pending+pending)
case('other environment never approved',targets=[dict(environment=dict(name='other-production',id=456),current_user_can_approve=True)])
case('review API failure remains failure',post_fail=True)
case('verification-only run cannot approve publication',build_job=False)
case('missing App validation rejected',runtime_job='missing')
case('skipped App validation rejected',runtime_job='skipped')
case('failed App validation rejected',runtime_job='failure')
case('missing middle normal stage rejected',world_job='missing')
case('failed middle normal stage rejected',world_job='failure')
case('skipped middle normal stage rejected',world_job='skipped')
case('missing final normal stage rejected',continuation_job='missing')
case('failed final normal stage rejected',continuation_job='failure')
case('skipped final normal stage rejected',continuation_job='skipped')
case('inspect approves without build or hash',ok=True,run='',digest='',operation='inspect')
case('inspect still requires reviewer token',token='',operation='inspect')
case('inspect API failure remains failure',post_fail=True,operation='inspect')
case('unknown operation rejected',operation='other')
case('personal tier requires actual short runtime but not long jobs',ok=True,world_job='skipped',continuation_job='skipped',quality='PERSONAL_TEST')
case('personal failed smoke still rejected',runtime_job='failure',world_job='skipped',continuation_job='skipped',quality='PERSONAL_TEST')
case('personal missing smoke still rejected',runtime_job='missing',world_job='skipped',continuation_job='skipped',quality='PERSONAL_TEST')
case('unknown quality rejected',quality='UNKNOWN')
results=[]
with tempfile.TemporaryDirectory(prefix='fengshen-review-test-') as td:
    root=Path(td);(root/'approve.sh').write_text(script)
    mock=root/'gh'
    mock.write_text('''#!/usr/bin/env python3
import json,os,sys
from pathlib import Path
c=json.loads(Path(os.environ['FIXTURE']).read_text());args=sys.argv[1:]
p=Path(os.environ['CALLS']);p.write_text((p.read_text() if p.exists() else '')+json.dumps(args)+'\\n')
if '--method' in args:
    body=json.loads(Path(args[args.index('--input')+1]).read_text())
    assert body['environment_ids']==[123] and body['state']=='approved'
    assert (c['digest'] in body['comment']) if c['operation']=='publish' else ('read-only' in body['comment'])
    if c['post_fail']:
        print('fixture HTTP 403',file=sys.stderr);sys.exit(1)
    print('[{"id":1,"environment":"fengshen-production"}]')
elif any(x.endswith('/jobs') for x in args):print(json.dumps({'jobs':[{'name':'build','status':'completed','conclusion':'success' if c['build_job'] else 'skipped'}]+[{'name':name,'status':'completed','conclusion':result} for name,result in [('runtime',c['runtime_job']),('runtime-world',c['world_job']),('runtime-continuation',c['continuation_job'])] if result!='missing']}))
elif any(x.endswith('/pending_deployments') for x in args):print(json.dumps(c['pending']))
elif any(x.endswith('/actions/runs/'+c['run']) for x in args):print(json.dumps(c['build']))
else:sys.exit(1)
''');mock.chmod(0o755)
    for command,body in [('seq','#!/bin/sh\nprintf "1\\n"\n'),('sleep','#!/bin/sh\nexit 0\n')]:
        path=root/command;path.write_text(body);path.chmod(0o755)
    for c in cases:
        fixture=root/'fixture.json';fixture.write_text(json.dumps(c))
        calls=root/'calls.jsonl';calls.unlink(missing_ok=True)
        env=dict(os.environ,PATH=str(root)+':'+os.environ['PATH'],GH_TOKEN=c['token'],BUILD_RUN_ID=c['run'],EXPECTED_SHA256=c['digest'],GITHUB_REPOSITORY='antpan5608-san/game-fengshen',GITHUB_SHA='fixture-commit',OPERATION=c['operation'],RELEASE_QUALITY=c['quality'],GITHUB_RUN_ID='999',RUNNER_TEMP=td,FIXTURE=str(fixture),CALLS=str(calls))
        r=subprocess.run(['bash',str(root/'approve.sh')],env=env,text=True,capture_output=True,timeout=10)
        posted=[json.loads(line) for line in calls.read_text().splitlines() if '--method' in json.loads(line)] if calls.exists() else []
        assert (r.returncode==0)==c['ok'],(c['name'],r.returncode,r.stderr)
        assert len(posted)==(1 if c['ok'] or c['post_fail'] else 0),c['name']
        if c['post_fail']:assert 'environment approved through' not in r.stdout
        calls_list=[json.loads(line) for line in calls.read_text().splitlines()] if calls.exists() else []
        if c['operation']=='inspect':assert not any('/actions/runs/' in str(x) and 'pending_deployments' not in str(x) for x in calls_list)
        print('PASS:',c['name']);results.append(dict(name=c['name'],passed=True))
output=Path('artifacts/ci');output.mkdir(parents=True,exist_ok=True)
(output/'auto-approval-test-results.json').write_text(json.dumps(results,indent=2)+'\n')
print('Auto-approval tests:',len(results),'passed')
