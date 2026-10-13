#!/usr/bin/env bash
# Manual QA only: fixed database, port, migration location and Java version.
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
container=san-miguel-v20-audit
restart=false
create_admin=false
for argument in "$@"; do
  case "$argument" in
    --restart) restart=true ;;
    --create-admin) create_admin=true ;;
    *) echo 'Uso: start-manual-isolated.sh [--restart] [--create-admin]' >&2; exit 1 ;;
  esac
done
java_bin=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
jar="$repo_dir/backend/target/san-miguel-api-0.0.1-SNAPSHOT.jar"
[[ -x "$java_bin" && -f "$jar" ]] || { echo 'Falta Java 21 o el JAR validado.' >&2; exit 1; }
identity_config="$repo_dir/backend/.env.local"
[[ -r "$identity_config" ]] || { echo 'Falta backend/.env.local con la clave maestra original. No se generará ni sustituirá una clave automáticamente.' >&2; exit 1; }
[[ "$(stat -c '%a' "$identity_config")" == 600 ]] || { echo 'backend/.env.local debe tener permisos 600.' >&2; exit 1; }
# Load the existing private properties file through Spring, without sourcing it
# or putting the key in arguments/logs. Avoid an accidental environment override.
unset IDENTITY_MASTER_KEY
"$java_bin" -version 2>&1 | head -1 | rg -q 'version "21\.'
[[ "$(docker inspect -f '{{index .Config.Labels "purpose"}}' "$container")" == san-miguel-v20-isolated ]] || { echo 'Etiqueta de aislamiento incorrecta.' >&2; exit 1; }
[[ "$(docker port "$container" 3306/tcp)" == '127.0.0.1:13317' ]] || { echo 'Puerto de MySQL incorrecto.' >&2; exit 1; }
docker exec -e MYSQL_PWD=isolated_v20_only "$container" mysqladmin -uroot ping --silent >/dev/null
[[ "$(docker exec -e MYSQL_PWD=isolated_v20_only "$container" mysql -uroot -Nse "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='v20_manual'")" == 1 ]] || { echo 'Falta la base manual validada v20_manual; no se creará ni restaurará automáticamente.' >&2; exit 1; }
export ADMIN_BOOTSTRAP_USERNAME='' ADMIN_BOOTSTRAP_PASSWORD=''
if [[ "$create_admin" == true ]]; then
  read -rp 'Nombre NUEVO para ADMIN_SISTEMA de pruebas: ' ADMIN_BOOTSTRAP_USERNAME
  [[ "$ADMIN_BOOTSTRAP_USERNAME" =~ ^[A-Za-z][A-Za-z0-9_.-]{2,63}$ ]] || { echo 'Usa 3–64 caracteres alfanuméricos, punto, guion o guion bajo.' >&2; exit 1; }
  exists=$(docker exec -e MYSQL_PWD=isolated_v20_only "$container" mysql -uroot v20_manual -Nse "SELECT COUNT(*) FROM app_user WHERE LOWER(username)=LOWER('$ADMIN_BOOTSTRAP_USERNAME')")
  [[ "$exists" == 0 ]] || { echo 'La cuenta ya existe. Se conserva su contraseña; elige otro nombre.' >&2; exit 1; }
  read -rsp 'Contraseña nueva (mínimo 8 caracteres, entrada oculta): ' ADMIN_BOOTSTRAP_PASSWORD
  echo
  [[ ${#ADMIN_BOOTSTRAP_PASSWORD} -ge 8 ]] || { echo 'Contraseña demasiado corta.' >&2; exit 1; }
  read -rsp 'Confirma la contraseña: ' confirmation
  echo
  [[ "$confirmation" == "$ADMIN_BOOTSTRAP_PASSWORD" ]] || { echo 'Las contraseñas no coinciden.' >&2; exit 1; }
  unset confirmation
fi
if [[ "$restart" == true ]]; then
  pid_file=/tmp/sm-manual-backend.pid
  [[ -r "$pid_file" ]] || { echo 'Falta el PID del backend manual; no se detendrá ningún proceso.' >&2; exit 1; }
  old_pid=$(cat "$pid_file")
  [[ "$old_pid" =~ ^[0-9]+$ ]] || exit 1
  if kill -0 "$old_pid" 2>/dev/null; then
    [[ "$(readlink -f "/proc/$old_pid/cwd")" == "$repo_dir" && "$(readlink -f "/proc/$old_pid/exe")" == "$(readlink -f "$java_bin")" ]] || { echo 'El PID no pertenece al backend manual Java 21 de este proyecto.' >&2; exit 1; }
    tr '\0' '\n' < "/proc/$old_pid/cmdline" | rg -q '^(.*/)?backend/target/san-miguel-api-0\.0\.1-SNAPSHOT\.jar$' || { echo 'El PID no corresponde al JAR esperado.' >&2; exit 1; }
    kill -TERM "$old_pid"
    for attempt in {1..30}; do
      kill -0 "$old_pid" 2>/dev/null || break
      [[ "$(ps -p "$old_pid" -o stat=)" == Z* ]] && break
      sleep 1
    done
  fi
fi
export DB_HOST=127.0.0.1 DB_PORT=13317 DB_NAME=v20_manual DB_USER=root DB_PASSWORD=isolated_v20_only SERVER_PORT=8080
export CORS_ORIGINS=http://localhost:4200,http://127.0.0.1:4200
export APP_STORAGE_PATH="$repo_dir/backend/target/manual/solicitudes" APP_DILIGENCIAS_PATH="$repo_dir/backend/target/manual/diligencias"
cd "$repo_dir"
echo "$$" > /tmp/sm-manual-backend.pid
exec "$java_bin" -jar "$jar" --server.address=127.0.0.1 \
  "--spring.config.import=file:$identity_config[.properties]" \
  --server.port=8080 --spring.profiles.active=manual-qa \
  '--spring.datasource.url=jdbc:mysql://127.0.0.1:13317/v20_manual?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=utf8' \
  --spring.datasource.username=root --spring.datasource.password=isolated_v20_only \
  --spring.flyway.locations=classpath:db/migration --spring.flyway.target=29 \
  --spring.flyway.baseline-on-migrate=false
