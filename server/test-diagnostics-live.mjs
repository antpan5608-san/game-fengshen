import {createRequire} from 'node:module';
const require=createRequire('C:/Users/antpan/Documents/language/package.json');const {Client}=require('ssh2');
const c=new Client();c.on('ready',()=>c.sftp((e,sftp)=>{if(e)throw e;sftp.fastPut('artifacts/cloud-server/diagnostics.test','/root/fengshen-stage/diagnostics.test',(err)=>{if(err)throw err;c.exec(`chmod 700 /root/fengshen-stage/diagnostics.test
/root/fengshen-stage/diagnostics.test -test.v
stat -c '%a %U %n' /var/lib/fengshen-remake-diagnostics /var/lib/fengshen-remake-diagnostics/diagnostics.json
curl -s -o /dev/null -w 'anonymous_admin=%{http_code}\\n' https://fleetpilots.com/fengshen-api/v1/admin/diagnostics/summary
curl -s -X POST -o /dev/null -w 'anonymous_write=%{http_code}\\n' https://fleetpilots.com/fengshen-api/v1/diagnostics/batches
curl -s -o /dev/null -w 'anonymous_private_file=%{http_code}\\n' https://fleetpilots.com/fengshen-api/diagnostics.json
curl -fsS https://fleetpilots.com/language-api/health >/dev/null && echo LANGUAGE_HEALTH_UNCHANGED
`,(x,stream)=>{if(x)throw x;stream.on('data',b=>process.stdout.write(b));stream.stderr.on('data',()=>{});stream.on('close',code=>{process.exitCode=code;c.end()})})})})).on('error',e=>{console.error(e.message);process.exitCode=1}).connect({host:'204.44.123.101',port:10080,username:process.env.REMOTE_USER,password:process.env.REMOTE_PASS,readyTimeout:30000});
