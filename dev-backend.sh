#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT_DIR/backend/.env.local"

# Read simple KEY=value files without executing shell code.
if [[ -f "$ENV_FILE" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
    if [[ ! "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]]; then
      printf 'Línea inválida en backend/.env.local; use únicamente KEY=valor.\n' >&2; exit 2
    fi
    key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
    case "$key" in
      DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|SERVER_PORT|JWT_SECRET|ADMIN_BOOTSTRAP_USERNAME|ADMIN_BOOTSTRAP_PASSWORD|IDENTITY_MASTER_KEY|CORS_ORIGINS|APP_STORAGE_PATH|RENIEC_MODE|RENIEC_BASE_URL|RENIEC_CLIENT_ID|RENIEC_CLIENT_SECRET|RENIEC_API_KEY|RENIEC_ALLOWED_HOSTS) ;;
      *) printf 'Variable no permitida en backend/.env.local: %s\n' "$key" >&2; exit 2 ;;
    esac
    if [[ "$value" =~ ^\'(.*)\'$ || "$value" =~ ^\"(.*)\"$ ]]; then value="${BASH_REMATCH[1]}"; fi
    if [[ "$value" == *'$('* || "$value" == *'`'* || "$value" == *$'\n'* ]]; then
      printf 'Valor no admitido en backend/.env.local para %s.\n' "$key" >&2; exit 2
    fi
    printf -v "$key" '%s' "$value"; export "$key"
  done < "$ENV_FILE"
fi

if [[ "${DB_NAME:-san_miguel_pruebas_v20}" == "san_miguel" ]]; then
  printf 'Inicio cancelado: DB_NAME apunta a la base histórica san_miguel.\n' >&2; exit 2
fi
export DB_NAME=san_miguel_pruebas_v20
export SPRING_PROFILES_ACTIVE=local

JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
if [[ ! -x "$JAVA21_HOME/bin/java" ]]; then
  printf 'No se encontró Java 21 en %s. Configure JAVA21_HOME.\n' "$JAVA21_HOME" >&2; exit 2
fi
export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

if ! command -v mvn >/dev/null 2>&1; then printf 'No se encontró Maven en PATH.\n' >&2; exit 2; fi
if ! command -v mysqladmin >/dev/null 2>&1; then printf 'Se requiere mysqladmin para comprobar MySQL.\n' >&2; exit 2; fi

DB_HOST="${DB_HOST:-localhost}"; DB_PORT="${DB_PORT:-3307}"; DB_USER="${DB_USER:-sanmiguel}"; DB_PASSWORD="${DB_PASSWORD:-sanmiguel_dev}"
export DB_HOST DB_PORT DB_USER DB_PASSWORD
printf 'Perfil Spring: %s | Base de datos: %s | Java: ' "$SPRING_PROFILES_ACTIVE" "$DB_NAME"
java -version 2>&1 | head -n 1

MYSQL_CNF="$(mktemp)"
chmod 600 "$MYSQL_CNF"
cleanup() { rm -f -- "$MYSQL_CNF"; }
trap cleanup EXIT
escaped_password="${DB_PASSWORD//\\/\\\\}"
escaped_password="${escaped_password//\"/\\\"}"
printf '[client]\nhost="%s"\nport=%s\nuser="%s"\npassword="%s"\n' "$DB_HOST" "$DB_PORT" "$DB_USER" "$escaped_password" > "$MYSQL_CNF"
if ! mysqladmin --defaults-extra-file="$MYSQL_CNF" --connect-timeout=3 ping --silent >/dev/null 2>&1; then
  printf 'MySQL no está disponible en %s:%s para la cuenta configurada.\n' "$DB_HOST" "$DB_PORT" >&2; exit 3
fi
cd "$ROOT_DIR/backend"
mvn spring-boot:run -Dspring-boot.run.profiles=local
