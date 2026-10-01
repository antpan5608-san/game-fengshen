#!/usr/bin/env bash
# Run as root on the approved VM after uploading the binary and schema to /root/fengshen-stage.
set -euo pipefail
stage=/root/fengshen-stage
service_dir=/opt/fengshen-remake/cloud
nginx_conf=/etc/nginx/sites-enabled/opsfleet-language.conf
role=fengshen_cloud
database=fengshen_remake

test -s "$stage/fengshen-server-linux-amd64"
test -s "$stage/schema.sql"
systemctl is-active --quiet postgresql
systemctl is-active --quiet nginx

if ! id fengshen-cloud >/dev/null 2>&1; then
    useradd --system --no-create-home --shell /usr/sbin/nologin fengshen-cloud
fi
install -d -o root -g root -m 755 "$service_dir"
install -m 755 "$stage/fengshen-server-linux-amd64" "$service_dir/fengshen-server"

if ! runuser -u postgres -- psql -Atqc "SELECT 1 FROM pg_roles WHERE rolname='$role'" | grep -qx 1; then
    db_password="$(openssl rand -hex 32)"
    runuser -u postgres -- psql -v ON_ERROR_STOP=1 -c "CREATE ROLE $role LOGIN PASSWORD '$db_password'" >/dev/null
    printf 'FENGSHEN_DATABASE_URL=postgres://%s:%s@127.0.0.1:5432/%s?sslmode=disable\n' \
        "$role" "$db_password" "$database" > /etc/fengshen-remake-cloud.env
    chmod 600 /etc/fengshen-remake-cloud.env
fi
test -s /etc/fengshen-remake-cloud.env
if ! runuser -u postgres -- psql -Atqc "SELECT 1 FROM pg_database WHERE datname='$database'" | grep -qx 1; then
    runuser -u postgres -- createdb -O "$role" "$database"
fi
runuser -u postgres -- psql -v ON_ERROR_STOP=1 -d "$database" < "$stage/schema.sql" >/dev/null
for table in accounts sessions save_slots; do
    runuser -u postgres -- psql -v ON_ERROR_STOP=1 -d "$database" -c "ALTER TABLE $table OWNER TO $role" >/dev/null
done

cat >/etc/systemd/system/fengshen-remake-cloud.service <<'UNIT'
[Unit]
Description=Fengshen Remake cloud save API
After=network.target postgresql.service
Requires=postgresql.service
[Service]
Type=simple
User=fengshen-cloud
Group=fengshen-cloud
EnvironmentFile=/etc/fengshen-remake-cloud.env
ExecStart=/opt/fengshen-remake/cloud/fengshen-server
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable fengshen-remake-cloud >/dev/null
systemctl restart fengshen-remake-cloud
systemctl is-active --quiet fengshen-remake-cloud
ready=0
for _ in 1 2 3 4 5; do
    if curl -fsS --max-time 2 http://127.0.0.1:8092/fengshen-api/healthz >/dev/null 2>&1; then ready=1; break; fi
    sleep 1
done
test "$ready" -eq 1

if ! grep -q 'location \^~ /fengshen-api/' "$nginx_conf"; then
    install -d -m 700 "$stage/nginx-backups"
    backup="$stage/nginx-backups/opsfleet-language.conf.$(date -u +%Y%m%d%H%M%S)"
    cp -p "$nginx_conf" "$backup"
    python3 - "$nginx_conf" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
s = p.read_text()
needle = '    client_max_body_size 320m;\n'
assert s.count(needle) == 1, 'unexpected nginx layout'
route = '''    # Fengshen cloud saves: separate local service and database.
    location ^~ /fengshen-api/ {
        client_max_body_size 64k;
        proxy_pass http://127.0.0.1:8092;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 25s;
        add_header Cache-Control "no-store" always;
    }

'''
p.write_text(s.replace(needle, needle + '\n' + route, 1))
PY
    if ! nginx -t; then cp -p "$backup" "$nginx_conf"; nginx -t; exit 1; fi
    systemctl reload nginx
fi

# Explicit account bootstrap only when absent; repeated deploys never reset credentials or saves.
if ! runuser -u postgres -- psql -d "$database" -Atqc "SELECT 1 FROM accounts WHERE username='antpan'" | grep -qx 1; then
    test -s "$stage/account-password.txt"
    set -a
    source /etc/fengshen-remake-cloud.env
    set +a
    "$service_dir/fengshen-server" -create-account antpan < "$stage/account-password.txt"
fi
rm -f "$stage/account-password.txt"

install -d -o postgres -g postgres -m 700 /var/backups/fengshen-remake
cat >/usr/local/sbin/fengshen-remake-backup <<'BACKUP'
#!/usr/bin/env bash
set -euo pipefail
dir=/var/backups/fengshen-remake
file="$dir/save-$(date -u +%Y%m%dT%H%M%SZ).dump"
runuser -u postgres -- pg_dump -Fc -f "$file" fengshen_remake
runuser -u postgres -- pg_restore -l "$file" >/dev/null
find "$dir" -maxdepth 1 -type f -name 'save-*.dump' -mtime +14 -delete
BACKUP
chmod 750 /usr/local/sbin/fengshen-remake-backup
printf '17 3 * * * root /usr/local/sbin/fengshen-remake-backup\n' >/etc/cron.d/fengshen-remake-backup
chmod 644 /etc/cron.d/fengshen-remake-backup
/usr/local/sbin/fengshen-remake-backup

curl -fsS --max-time 10 https://fleetpilots.com/fengshen-api/healthz >/dev/null
curl -fsS --max-time 10 https://fleetpilots.com/language-api/health >/dev/null
echo 'FENGSHEN_DEPLOY_OK; language_health=ok; isolated_db=ok; backup=ok'
