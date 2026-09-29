#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
DEMO_USER="${DEMO_USER:-municipio}"
DEMO_PASSWORD="${DEMO_PASSWORD:-municipio2026}"
FIXTURE="${FIXTURE:-backend/src/test/resources/fixture-minimal.pdf}"
WORK_DIR="$(mktemp -d)"
CODE=""

cleanup() {
  if [[ -n "$CODE" && -n "${TOKEN:-}" ]]; then
    curl -sS -o /dev/null -X DELETE -H "Authorization: Bearer $TOKEN" "$API_URL/api/solicitudes/$CODE" || true
  fi
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

command -v curl >/dev/null || { echo "curl es requerido" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq es requerido" >&2; exit 1; }
[[ -f "$FIXTURE" ]] || { echo "Fixture no encontrado: $FIXTURE" >&2; exit 1; }

login_code=$(curl -sS -o "$WORK_DIR/login.json" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$DEMO_USER\",\"password\":\"$DEMO_PASSWORD\"}" \
  "$API_URL/api/auth/login")
[[ "$login_code" == "200" ]] || { cat "$WORK_DIR/login.json" >&2; exit 1; }
TOKEN="$(jq -r '.token // empty' "$WORK_DIR/login.json")"
[[ -n "$TOKEN" ]] || { echo "Login no devolvió token" >&2; exit 1; }

list_code=$(curl -sS -o "$WORK_DIR/list.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$API_URL/api/solicitudes")
[[ "$list_code" == "200" ]] || { cat "$WORK_DIR/list.json" >&2; exit 1; }

DNI="$(printf '%08d' "$(( $(date +%s) % 100000000 ))")"
payload="{\"dni\":\"$DNI\",\"nombres\":\"Smoke\",\"apellidos\":\"Prueba\",\"telefono\":\"999999999\",\"direccion\":\"Calle Smoke 1\",\"referencia\":\"Prueba automatizada\",\"documento\":\"\",\"documentoTamano\":\"\",\"latitud\":-12.089,\"longitud\":-77.074,\"fecha\":\"$(date +%F)\",\"hora\":\"10:00\",\"fiscalizadorId\":\"\",\"estado\":\"En espera\",\"observaciones\":\"Smoke test\"}"
create_code=$(curl -sS -o "$WORK_DIR/create.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  -F "solicitud=$payload;type=application/json" \
  -F "documento=@$FIXTURE;type=application/pdf;filename=smoke-test.pdf" \
  "$API_URL/api/solicitudes")
[[ "$create_code" == "201" ]] || { cat "$WORK_DIR/create.json" >&2; exit 1; }
CODE="$(jq -r '.codigo // empty' "$WORK_DIR/create.json")"
[[ -n "$CODE" ]] || { echo "La creación no devolvió código" >&2; exit 1; }

state_code=$(curl -sS -o "$WORK_DIR/state.json" -w '%{http_code}' -X PATCH \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"estado":"Verificado"}' "$API_URL/api/solicitudes/$CODE/estado")
[[ "$state_code" == "200" ]] || { cat "$WORK_DIR/state.json" >&2; exit 1; }

document_code=$(curl -sS -o "$WORK_DIR/document.pdf" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$API_URL/api/solicitudes/$CODE/documento")
[[ "$document_code" == "200" ]] || exit 1
grep -qi '%PDF-' "$WORK_DIR/document.pdf" || exit 1

delete_code=$(curl -sS -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $TOKEN" "$API_URL/api/solicitudes/$CODE")
[[ "$delete_code" == "204" ]] || exit 1
CODE=""
echo "Smoke OK: login, listado, creación, estado, documento y borrado temporal"
