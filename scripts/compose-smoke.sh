#!/usr/bin/env bash
# Smoke test for the Docker stack (STEPS.md, Step 19): builds and starts docker-compose.yml, then
# checks that every service is healthy and the API answers through nginx. Exits non-zero on failure.
#
#   scripts/compose-smoke.sh          leave the stack running afterwards
#   scripts/compose-smoke.sh --down   stop it afterwards (data volume kept)
set -euo pipefail
cd "$(dirname "$0")/.."

FRONTEND="http://localhost:${FRONTEND_PORT:-3000}"
BACKEND="http://localhost:${BACKEND_PORT:-8080}"
failed=0

check() { # name, command...
  local name=$1; shift
  if "$@" > /dev/null 2>&1; then echo "  ok    $name"; else echo "  FAIL  $name"; failed=1; fi
}

echo "starting the stack..."
docker compose up --build -d --wait --wait-timeout 300 postgres backend frontend || failed=1

echo "checks:"
check "backend /actuator/health is UP" sh -c "curl -fsS $BACKEND/actuator/health | grep -q '\"status\":\"UP\"'"
check "frontend serves the app on :3000" sh -c "curl -fsS $FRONTEND/ | grep -q '<div id=\"root\">'"
check "a deep link falls back to the app" sh -c "curl -fsS $FRONTEND/races/2024/1/replay | grep -q '<div id=\"root\">'"
check "/api/seasons through nginx returns 200" curl -fsS "$FRONTEND/api/seasons"

if [[ "${1:-}" == "--down" ]]; then docker compose down; fi
if [[ $failed -ne 0 ]]; then
  echo "smoke test FAILED"; docker compose ps; exit 1
fi
echo "smoke test passed"
