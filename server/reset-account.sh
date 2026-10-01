#!/usr/bin/env bash
# On the Fengshen VM only; password is supplied in a staged private file.
set -euo pipefail
test -s /root/fengshen-stage/account-password.txt
set -a
source /etc/fengshen-remake-cloud.env
set +a
/opt/fengshen-remake/cloud/fengshen-server -reset-account antpan < /root/fengshen-stage/account-password.txt
rm -f /root/fengshen-stage/account-password.txt
echo 'FENGSHEN_ACCOUNT_PASSWORD_ROTATED'
