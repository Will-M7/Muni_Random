#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export JAVA_HOME="${SAN_MIGUEL_JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
if ! java -version 2>&1 | head -1 | rg -q 'version "21\.'; then
  echo 'Se requiere Java 21. Configura SAN_MIGUEL_JAVA21_HOME.' >&2
  exit 1
fi
container=san-miguel-v20-audit
if docker inspect "$container" >/dev/null 2>&1; then
  [[ "$(docker inspect -f '{{index .Config.Labels "purpose"}}' "$container")" == san-miguel-v20-isolated ]] || { echo 'Contenedor sin etiqueta de aislamiento.' >&2; exit 1; }
  if [[ "$(docker inspect -f '{{.State.Running}}' "$container")" != true ]]; then docker start "$container" >/dev/null; fi
  [[ "$(docker port "$container" 3306/tcp)" == '127.0.0.1:13317' ]] || { echo 'Puerto aislado incompatible.' >&2; exit 1; }
else
  docker run -d --name "$container" --label purpose=san-miguel-v20-isolated \
    -e MYSQL_ROOT_PASSWORD=isolated_v20_only -p 127.0.0.1:13317:3306 mysql:8.4
fi
ready=false
for attempt in {1..45}; do
  if docker exec -e MYSQL_PWD=isolated_v20_only "$container" mysqladmin -uroot ping --silent >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'MySQL aislado no respondió.' >&2; exit 1; }
docker exec -e MYSQL_PWD=isolated_v20_only "$container" mysql -uroot -e 'CREATE DATABASE IF NOT EXISTS v20_clean CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
backup_path="${SAN_MIGUEL_V20_BACKUP:-/home/willian/respaldos_muni/san_miguel_20261013_011235.sql}"
[[ -r "$backup_path" ]] || { echo 'Falta el respaldo para la prueba de rechazo seguro.' >&2; exit 1; }
# Never restore over an existing database. Keep evidence from every rehearsal.
exists=$(docker exec -e MYSQL_PWD=isolated_v20_only "$container" mysql -uroot -Nse "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='v20_backup'")
if [[ "$exists" == 0 ]]; then
  docker exec -e MYSQL_PWD=isolated_v20_only "$container" mysql -uroot -e 'CREATE DATABASE v20_backup CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
  docker exec -i -e MYSQL_PWD=isolated_v20_only "$container" mysql -uroot v20_backup < "$backup_path"
fi
cd "$repo_dir"
mvn -B -f backend/pom.xml package -Dmunicipal.integration=true \
  '-Dmunicipal.test.jdbc-url=jdbc:mysql://127.0.0.1:13317/v20_clean?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=utf8' \
  -Dspring.datasource.username=root -Dspring.datasource.password=isolated_v20_only
# No clean, drop, removal of containers/volumes, or production locations.
