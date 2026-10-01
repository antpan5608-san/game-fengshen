// Update only the existing Fengshen cloud binary; no schema, account or nginx changes.
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {join} from 'node:path';

const local=process.argv[2];
if(!local || !process.env.REMOTE_HOST || !process.env.REMOTE_PASS)throw Error('Local binary and protected SSH environment required');
const language=process.env.LANGUAGE_WORKSPACE || 'C:/Users/antpan/Documents/language';
const require=createRequire(pathToFileURL(join(language,'package.json')));
const {Client}=require('ssh2');
const sha=createHash('sha256').update(readFileSync(local)).digest('hex');
const stage='/root/fengshen-stage/fengshen-server-linux-amd64';
const target='/opt/fengshen-remake/cloud/fengshen-server';
const previous='/root/fengshen-stage/fengshen-server.previous';
const quote=value=>`'${value.replaceAll("'","'\\''")}'`;
const script=`set -eu
if test -s /root/fengshen-stage/diagnostics-setup.sh; then bash /root/fengshen-stage/diagnostics-setup.sh; fi
test "$(sha256sum ${stage} | cut -d' ' -f1)" = '${sha}'
cp -p ${target} ${previous}
install -m 755 ${stage} ${target}
healthy=0
if systemctl restart fengshen-remake-cloud; then
  for attempt in 1 2 3 4 5 6 7 8 9 10; do
    if curl -fsS --max-time 2 http://127.0.0.1:8092/fengshen-api/healthz >/dev/null 2>&1; then healthy=1; break; fi
    sleep 1
  done
fi
if test "$healthy" -ne 1; then
  journalctl -u fengshen-remake-cloud -n 10 --no-pager >&2
  install -m 755 ${previous} ${target}
  systemctl restart fengshen-remake-cloud
  exit 1
fi
curl -fsS --max-time 10 https://fleetpilots.com/fengshen-api/healthz >/dev/null
curl -fsS --max-time 10 https://fleetpilots.com/language-api/health >/dev/null
echo FENGSHEN_CLOUD_UPDATE_OK`; 
const conn=new Client();
conn.on('ready',()=>conn.sftp((error,sftp)=>{
  if(error){console.error(error.message);conn.end();process.exitCode=1;return;}
  sftp.fastPut(new URL('./diagnostics-setup.sh',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1'),'/root/fengshen-stage/diagnostics-setup.sh',(setupError)=>{
    if(setupError){console.error(setupError.message);conn.end();process.exitCode=1;return;}
  sftp.fastPut(local,stage,(uploadError)=>{
    if(uploadError){console.error(uploadError.message);conn.end();process.exitCode=1;return;}
    conn.exec(`bash -c ${quote(script)}`,(execError,stream)=>{
      if(execError){console.error(execError.message);conn.end();process.exitCode=1;return;}
      stream.on('data',chunk=>process.stdout.write(chunk));
      stream.stderr.on('data',chunk=>process.stderr.write(chunk));
      stream.on('close',code=>{conn.end();if(code!==0)process.exitCode=code||1;});
    });
  });
  });
})).on('error',error=>{console.error(error.message);process.exitCode=1;}).connect({
  host:process.env.REMOTE_HOST,port:Number(process.env.REMOTE_PORT||22),
  username:process.env.REMOTE_USER||'root',password:process.env.REMOTE_PASS,
  readyTimeout:120000,keepaliveInterval:10000,keepaliveCountMax:12,
});
