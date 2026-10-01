// Fallback for servers whose SSH SFTP subsystem stalls. Reuses Language's installed ssh2.
const fs = require('node:fs');
const {Client} = require('C:/Users/antpan/Documents/language/node_modules/ssh2');
const [local, remote] = process.argv.slice(2);
if (!local || !/^\/root\/fengshen-stage\/[a-zA-Z0-9._-]+$/.test(remote)) throw new Error('invalid staged upload');
const conn = new Client();
conn.on('ready', () => {
  conn.exec(`cat > '${remote}'`, (error, stream) => {
    if (error) { console.error(error.message); conn.end(); process.exitCode=1; return; }
    const input=fs.createReadStream(local);
    input.on('error', error => { console.error(error.message); stream.destroy(); });
    let finished=false;
    const done=(code) => {if(finished)return;finished=true;conn.end();process.exitCode=code||0;if(code===0)console.log(`staged ${remote}`);};
    stream.on('close', done);
    // Some SSH servers consume EOF but never close an exec channel. The caller
    // verifies the uploaded SHA-256 independently before executing it.
    input.on('end', () => setTimeout(() => done(0), 15000));
    stream.stderr.on('data', chunk => process.stderr.write(chunk));
    input.pipe(stream);
  });
}).on('error', error => { console.error(error.message); process.exitCode=1; });
conn.connect({host:process.env.REMOTE_HOST,port:Number(process.env.REMOTE_PORT||22),
  username:process.env.REMOTE_USER,password:process.env.REMOTE_PASS,
  readyTimeout:120000,keepaliveInterval:10000,keepaliveCountMax:12});
