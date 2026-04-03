#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/compose.prod.yml"
ENV_FILE="${1:-${SCRIPT_DIR}/.env.prod}"

if [ ! -f "${COMPOSE_FILE}" ]; then
  echo "ERROR: compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: env file not found: ${ENV_FILE}" >&2
  echo "Copy .env.prod.example to .env.prod and fill secrets." >&2
  exit 1
fi

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

wait_for_service() {
  local service="$1"
  local timeout_sec="${2:-180}"
  local started_at
  started_at="$(date +%s)"

  while true; do
    local container_id
    container_id="$(compose ps -q "${service}" 2>/dev/null || true)"
    if [ -z "${container_id}" ]; then
      if [ $(( $(date +%s) - started_at )) -ge "${timeout_sec}" ]; then
        echo "ERROR: service ${service} did not start in time" >&2
        compose ps
        exit 1
      fi
      sleep 2
      continue
    fi

    local state health
    state="$(docker inspect --format '{{.State.Status}}' "${container_id}" 2>/dev/null || echo unknown)"
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${container_id}" 2>/dev/null || echo none)"

    if [ "${state}" = "running" ] && { [ "${health}" = "healthy" ] || [ "${health}" = "none" ]; }; then
      echo "OK: ${service} is ${state}/${health}"
      return 0
    fi

    if [ "${state}" = "exited" ] || [ "${health}" = "unhealthy" ]; then
      echo "ERROR: ${service} is ${state}/${health}" >&2
      compose logs --no-color --tail=200 "${service}" || true
      exit 1
    fi

    if [ $(( $(date +%s) - started_at )) -ge "${timeout_sec}" ]; then
      echo "ERROR: timed out waiting for ${service} (last state: ${state}/${health})" >&2
      compose logs --no-color --tail=200 "${service}" || true
      exit 1
    fi

    sleep 3
  done
}

echo "==> Pull images"
compose pull

echo "==> Start/update services"
compose up -d --remove-orphans

echo "==> Wait for healthchecks"
wait_for_service postgres 180
wait_for_service backend 240
wait_for_service frontend 120

echo "==> Deployment status"
compose ps

echo "Deployment completed successfully"
