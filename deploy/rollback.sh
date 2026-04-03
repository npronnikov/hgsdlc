#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/compose.prod.yml"
ENV_FILE="${1:-${SCRIPT_DIR}/.env.prod}"

if [ ! -f "${ENV_FILE}" ]; then
  echo "ERROR: env file not found: ${ENV_FILE}" >&2
  exit 1
fi

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

get_env_value() {
  local key="$1"
  grep -E "^${key}=" "${ENV_FILE}" | tail -1 | cut -d'=' -f2-
}

set_env_value() {
  local key="$1"
  local value="$2"
  local tmp_file
  tmp_file="$(mktemp)"

  awk -v key="${key}" -v value="${value}" '
    BEGIN { updated = 0 }
    $0 ~ "^" key "=" {
      print key "=" value
      updated = 1
      next
    }
    { print }
    END {
      if (updated == 0) {
        print key "=" value
      }
    }
  ' "${ENV_FILE}" > "${tmp_file}"

  mv "${tmp_file}" "${ENV_FILE}"
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

current_backend="$(get_env_value BACKEND_TAG)"
previous_backend="$(get_env_value BACKEND_PREVIOUS_TAG)"
current_frontend="$(get_env_value FRONTEND_TAG)"
previous_frontend="$(get_env_value FRONTEND_PREVIOUS_TAG)"

if [ -z "${previous_backend}" ] || [ -z "${previous_frontend}" ]; then
  echo "ERROR: BACKEND_PREVIOUS_TAG and FRONTEND_PREVIOUS_TAG must be set in ${ENV_FILE}" >&2
  exit 1
fi

echo "==> Rolling back backend: ${current_backend} -> ${previous_backend}"
echo "==> Rolling back frontend: ${current_frontend} -> ${previous_frontend}"

set_env_value BACKEND_TAG "${previous_backend}"
set_env_value BACKEND_PREVIOUS_TAG "${current_backend}"
set_env_value FRONTEND_TAG "${previous_frontend}"
set_env_value FRONTEND_PREVIOUS_TAG "${current_frontend}"

echo "==> Pull rollback images"
compose pull backend frontend

echo "==> Restart services with rollback tags"
compose up -d backend frontend

echo "==> Wait for healthchecks"
wait_for_service backend 240
wait_for_service frontend 120

echo "Rollback completed successfully"
compose ps
