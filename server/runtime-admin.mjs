// Protected SSH bridge: credentials stay in process environment, query output contains no secrets.
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
const require=createRequire(import.meta.url);
let Client;
try { ({Client}=require('ssh2')); }
catch (e) {
 if(process.env.GITHUB_ACTIONS==='true')throw Error('Run npm ci --prefix server before cloud diagnostics');
 ({Client}=createRequire('C:/Users/antpan/Documents/language/package.json')('ssh2'));
}
const mode=process.argv[2]||'summary';
if(!['summary','retention','releases'].includes(mode))throw Error('Unsupported diagnostic operation');
const payload=mode==='releases'?JSON.stringify(JSON.parse(readFileSync(process.argv[3],'utf8').replace(/^\uFEFF/,''))):'';
const queryID=mode==='summary'?(process.argv[3]||''):'';
if(queryID&&!/^[a-zA-Z0-9._:-]{1,100}$/.test(queryID))throw Error('Invalid event ID');
const program=`import json,os,urllib.request,urllib.error
env={}
for line in open('/etc/fengshen-remake-cloud.env'):
 if '=' in line:
  k,v=line.strip().split('=',1);env[k]=v
token=env.get('FENGSHEN_DIAGNOSTICS_ADMIN','')
url='http://127.0.0.1:8092/fengshen-api/v1/admin/diagnostics/${mode}${queryID?'?eventID='+encodeURIComponent(queryID):''}'
if not token:
 print(json.dumps({'status':'NOT_AVAILABLE','reason':'diagnostics_not_configured'}));raise SystemExit(0)
data=${mode==='releases'?`json.dumps(json.loads(${JSON.stringify(payload)})).encode()`:'None'}
req=urllib.request.Request(url,data=data,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
try:
 with urllib.request.urlopen(req,timeout=25) as response: print(response.read(2097152).decode())
except urllib.error.HTTPError as e:
 print(json.dumps({'status':'UNAVAILABLE','httpStatus':e.code,'reason':'diagnostics_query_failed'}))
`;
const script=`python3 - <<'FENGSHEN_READ_ONLY_QUERY'\n${program}\nFENGSHEN_READ_ONLY_QUERY`;
const c=new Client();
c.on('ready',()=>c.exec(script,(e,s)=>{if(e)throw e;s.on('data',x=>process.stdout.write(x));s.stderr.on('data',()=>{});s.on('close',code=>{process.exitCode=code;c.end();});})).on('error',e=>{console.error('Protected remote operation unavailable: '+e.message);process.exitCode=1;}).connect({host:process.env.REMOTE_HOST||'204.44.123.101',port:Number(process.env.REMOTE_PORT||10080),username:process.env.REMOTE_USER||'root',password:process.env.REMOTE_PASS,readyTimeout:30000});
