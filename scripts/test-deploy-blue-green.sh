#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/deploy-blue-green.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

make_fixture() {
  local dir="$1"
  mkdir -p "$dir/deploy/nginx" "$dir/bin"
  cp "$ROOT/deploy/nginx/upstream.conf" "$dir/deploy/nginx/upstream.conf"
  cp "$ROOT/docker-compose.yml" "$dir/docker-compose.yml"
  : > "$dir/production.env"
  cat > "$dir/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$DOCKER_CALL_LOG"
if [[ "$1" == "inspect" ]]; then
  echo "${DOCKER_HEALTH:-healthy}"
  exit 0
fi
if [[ "$1" == "compose" && "$*" == *"nginx -t"* && "${DOCKER_FAIL_NGINX_TEST:-0}" == "1" ]]; then
  echo "simulated nginx validation failure" >&2
  exit 1
fi
if [[ "$1" == "compose" && "$*" == *"nginx -s reload"* && "${DOCKER_FAIL_NGINX_RELOAD:-0}" == "1" ]]; then
  echo "simulated nginx reload failure" >&2
  exit 1
fi
exit 0
DOCKER
  chmod +x "$dir/bin/docker"
}

SUCCESS="$TMP/success"
make_fixture "$SUCCESS"
(
  cd "$SUCCESS"
  PATH="$SUCCESS/bin:$PATH" \
  DI_ENV_FILE="$SUCCESS/production.env" \
  DOCKER_CALL_LOG="$SUCCESS/docker.log" \
  bash "$SCRIPT" "test-image-sha"
)
grep -q "digital-invitation-backend-green" "$SUCCESS/deploy/nginx/upstream.conf"
grep -q "nginx -s reload" "$SUCCESS/docker.log"

ROLLBACK="$TMP/rollback"
make_fixture "$ROLLBACK"
if (
  cd "$ROLLBACK"
  PATH="$ROLLBACK/bin:$PATH" \
  DI_ENV_FILE="$ROLLBACK/production.env" \
  DOCKER_CALL_LOG="$ROLLBACK/docker.log" \
  DOCKER_FAIL_NGINX_TEST=1 \
  bash "$SCRIPT" "test-image-sha"
); then
  echo "Expected Nginx validation failure did not fail deployment" >&2
  exit 1
fi
grep -q "digital-invitation-backend-blue" "$ROLLBACK/deploy/nginx/upstream.conf"
grep -q "docker rm -f digital-invitation-backend-green" "$ROLLBACK/docker.log"

HEALTH_FAILURE="$TMP/health-failure"
make_fixture "$HEALTH_FAILURE"
if (
  cd "$HEALTH_FAILURE"
  PATH="$HEALTH_FAILURE/bin:$PATH" \
  DI_ENV_FILE="$HEALTH_FAILURE/production.env" \
  DOCKER_CALL_LOG="$HEALTH_FAILURE/docker.log" \
  DOCKER_HEALTH=unhealthy \
  bash "$SCRIPT" "test-image-sha"
); then
  echo "Expected unhealthy candidate to fail deployment" >&2
  exit 1
fi
grep -q "digital-invitation-backend-blue" "$HEALTH_FAILURE/deploy/nginx/upstream.conf"
echo "Blue-green deployment switch and rollback tests passed."
