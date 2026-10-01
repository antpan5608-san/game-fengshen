#!/usr/bin/env bash
# Only Fengshen's env/drop-in/private state; no account/database/nginx/Language edits.
set -eu
python3 - <<'PY'
import os,secrets
p='/etc/fengshen-remake-cloud.env'
s=open(p).read()
if 'FENGSHEN_DIAGNOSTICS_ADMIN=' not in s:
 s+='\nFENGSHEN_DIAGNOSTICS_ADMIN='+secrets.token_hex(32)+'\n'
if 'FENGSHEN_DIAGNOSTICS_DIR=' not in s:
 s+='FENGSHEN_DIAGNOSTICS_DIR=/var/lib/fengshen-remake-diagnostics\n'
with open(p,'w') as f:f.write(s)
os.chmod(p,0o600)
PY
install -d -m 755 /etc/systemd/system/fengshen-remake-cloud.service.d
cat >/etc/systemd/system/fengshen-remake-cloud.service.d/diagnostics.conf <<'UNIT'
[Service]
StateDirectory=fengshen-remake-diagnostics
StateDirectoryMode=0700
UNIT
systemctl daemon-reload
echo FENGSHEN_PRIVATE_DIAGNOSTICS_CONFIGURED
