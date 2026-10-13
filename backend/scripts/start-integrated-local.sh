#!/usr/bin/env bash
# Keeps the existing manual QA database/files. No production locations or V27.
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_dir"
[[ "$(docker inspect -f '{{index .Config.Labels "purpose"}}' san-miguel-v20-audit)" == san-miguel-v20-isolated ]] || exit 1
[[ "$(docker port san-miguel-v20-audit 3306/tcp)" == '127.0.0.1:13317' ]] || exit 1
[[ "$(docker exec -e MYSQL_PWD=isolated_v20_only san-miguel-v20-audit mysql -uroot -Nse "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='v20_manual'")" == 1 ]] || exit 1
docker image inspect sm-integration-20261013-backend:latest >/dev/null
docker image inspect sm-integration-20261013-frontend:latest >/dev/null
[[ -r .local/integration-20261013/identity_master_key ]] || { echo 'Falta el secreto existente de identidad. No se generará una clave.' >&2; exit 1; }
python3 - <<'PY'
import base64,hmac,pathlib
config=pathlib.Path('backend/.env.local')
values=dict(line.split('=',1) for line in config.read_text().splitlines() if '=' in line and not line.startswith('#'))
original=values.get('IDENTITY_MASTER_KEY','').strip().strip('\"\'')
mounted=pathlib.Path('.local/integration-20261013/identity_master_key').read_text().strip()
if len(base64.b64decode(original,validate=True))!=32 or not hmac.compare_digest(original,mounted):
 raise SystemExit('Las claves existentes no coinciden. Se detiene el arranque; no se reemplazará ninguna.')
PY
if [[ ! -e .local/integration-20261013/manual_db_password ]]; then
  (umask 077; printf '%s' 'isolated_v20_only' > .local/integration-20261013/manual_db_password)
fi
setfacl -m u:10001:r .local/integration-20261013/manual_db_password .local/integration-20261013/identity_master_key
if ! docker network inspect sm-manual-private >/dev/null 2>&1; then
  docker network create --internal sm-manual-private >/dev/null
fi
if [[ "$(docker inspect -f '{{if index .NetworkSettings.Networks "sm-manual-private"}}yes{{end}}' san-miguel-v20-audit)" != yes ]]; then
  docker network connect --alias mysql-audit sm-manual-private san-miguel-v20-audit
fi
# Stop only detached Java/npm processes previously created for this checkout.
python3 - "$repo_dir" <<'PY'
import os, pathlib, signal, sys, time, subprocess, re
repo=sys.argv[1]
for kind in ['backend','frontend']:
 f=pathlib.Path('/tmp/sm-manual-'+kind+'.pid')
 if not f.exists(): continue
 pid=int(f.read_text()); proc=pathlib.Path('/proc')/str(pid)
 if not proc.exists() or (proc/'stat').read_text().split()[2]=='Z':continue
 cmd=(proc/'cmdline').read_bytes().replace(b'\0',b' ').decode()
 if os.path.realpath(proc/'cwd')!=repo or not (('san-miguel-api-0.0.1-SNAPSHOT.jar' in cmd) if kind=='backend' else ('npm start' in cmd and '4200' in cmd)):
  continue  # Stale PID files must never stop a foreign process or block Docker.
 if os.getpgid(pid)!=pid:raise SystemExit('Grupo de procesos no aislado')
 os.killpg(pid,signal.SIGTERM)
 for _ in range(50):
  if not proc.exists() or (proc/'stat').read_text().split()[2]=='Z':break
  time.sleep(.1)
# A previous npm parent can exit while its Angular child retains the port.
# Inspect the listener and stop it only if it belongs to THIS checkout's ng serve.
listeners=subprocess.check_output(['ss','-ltnp','sport = :4200'],text=True)
for candidate in set(re.findall(r'pid=(\d+)',listeners)):
 proc=pathlib.Path('/proc')/candidate
 if not proc.exists():continue
 cmd=(proc/'cmdline').read_bytes().replace(b'\0',b' ').decode()
 if os.path.realpath(proc/'cwd')==repo and 'ng serve' in cmd and '4200' in cmd:
  os.kill(int(candidate),signal.SIGTERM)
  for _ in range(50):
   if not proc.exists() or (proc/'stat').read_text().split()[2]=='Z':break
   time.sleep(.1)
PY
if ! docker volume inspect sm-manual-uploads-20261013 >/dev/null 2>&1; then
  docker volume create sm-manual-uploads-20261013 >/dev/null
  # Additive copy into a NEW empty volume; the source stays read-only and intact.
  docker run --name sm-manual-copy-20261013 \
    --mount "type=bind,source=$repo_dir/backend/target/manual,target=/source,readonly" \
    --mount type=volume,source=sm-manual-uploads-20261013,target=/destination \
    node:24-bookworm sh -c 'cp -a /source/. /destination/ && chown -R 10001:10001 /destination'
fi
docker compose -p sm-manual-integrated -f compose.manual.yml up -d
