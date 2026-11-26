#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${1:?Usage: deploy-blue-green.sh <immutable-image-tag>}"
ENV_FILE="${DI_ENV_FILE:-.env}"
PROJECT_NAME="${DI_COMPOSE_PROJECT:-digitalinvitation}"
UPSTREAM_FILE="deploy/nginx/upstream.conf"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Deployment environment file not found. Set DI_ENV_FILE to the host's protected .env file."
  exit 2
fi
if [[ ! -f "$UPSTREAM_FILE" ]]; then
  echo "Nginx upstream configuration is missing: $UPSTREAM_FILE"
  exit 2
fi

compose() {
  docker compose --project-name "$PROJECT_NAME" --env-file "$ENV_FILE" -f docker-compose.yml "$@"
}

ACTIVE="$(grep -Eo 'digital-invitation-backend-(blue|green)' "$UPSTREAM_FILE" | head -n1 || true)"
if [[ "$ACTIVE" == "digital-invitation-backend-blue" ]]; then
  TARGET="digital-invitation-backend-green"
elif [[ "$ACTIVE" == "digital-invitation-backend-green" ]]; then
  TARGET="digital-invitation-backend-blue"
else
  echo "Could not identify the active backend in $UPSTREAM_FILE"
  exit 2
fi

echo "Pulling immutable image $IMAGE_TAG"
DIGITALINVITATION_IMAGE_TAG="$IMAGE_TAG" compose pull backend

echo "Starting candidate backend $TARGET"
docker rm -f "$TARGET" >/dev/null 2>&1 || true
DIGITALINVITATION_IMAGE_TAG="$IMAGE_TAG" compose run -d --no-deps --name "$TARGET" backend >/dev/null

HEALTH="starting"
for attempt in $(seq 1 60); do
  HEALTH="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$TARGET" 2>/dev/null || echo missing)"
  if [[ "$HEALTH" == "healthy" ]]; then break; fi
  if [[ "$HEALTH" == "unhealthy" || "$HEALTH" == "missing" || "$HEALTH" == "exited" ]]; then
    docker logs --tail 100 "$TARGET" || true
    docker rm -f "$TARGET" >/dev/null 2>&1 || true
    echo "Candidate backend failed its health check; the active backend was not changed."
    exit 1
  fi
  sleep 5
done
if [[ "$HEALTH" != "healthy" ]]; then
  docker logs --tail 100 "$TARGET" || true
  docker rm -f "$TARGET" >/dev/null 2>&1 || true
  echo "Candidate backend did not become healthy; the active backend was not changed."
  exit 1
fi

compose up -d --no-deps frontend

cp "$UPSTREAM_FILE" "${UPSTREAM_FILE}.previous"
sed -E "s/digital-invitation-backend-(blue|green)/$TARGET/" "$UPSTREAM_FILE" > "${UPSTREAM_FILE}.next"
mv "${UPSTREAM_FILE}.next" "$UPSTREAM_FILE"

if ! compose exec -T frontend nginx -t; then
  mv "${UPSTREAM_FILE}.previous" "$UPSTREAM_FILE"
  compose exec -T frontend nginx -s reload || true
  docker rm -f "$TARGET" >/dev/null 2>&1 || true
  echo "Nginx validation failed; restored the previous backend."
  exit 1
fi
if ! compose exec -T frontend nginx -s reload; then
  mv "${UPSTREAM_FILE}.previous" "$UPSTREAM_FILE"
  compose exec -T frontend nginx -s reload || true
  docker rm -f "$TARGET" >/dev/null 2>&1 || true
  echo "Nginx reload failed; restored the previous backend."
  exit 1
fi

if ! compose exec -T frontend wget -qO- --timeout=10 http://127.0.0.1/actuator/health >/dev/null; then
  mv "${UPSTREAM_FILE}.previous" "$UPSTREAM_FILE"
  compose exec -T frontend nginx -s reload || true
  docker rm -f "$TARGET" >/dev/null 2>&1 || true
  echo "Nginx smoke test failed; restored the previous backend."
  exit 1
fi

rm -f "${UPSTREAM_FILE}.previous"
echo "Deployment successful. $TARGET is active; the previous backend remains available for rollback."
