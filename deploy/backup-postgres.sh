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

read_env() {
  local key="$1"
  local default_value="$2"
  local value
  value="$(grep -E "^${key}=" "${ENV_FILE}" | tail -1 | cut -d'=' -f2- || true)"
  if [ -z "${value}" ]; then
    echo "${default_value}"
  else
    echo "${value}"
  fi
}

POSTGRES_USER="$(read_env POSTGRES_USER sdlc)"
POSTGRES_DB="$(read_env POSTGRES_DB sdlc)"
BACKUP_DIR="$(read_env BACKUP_DIR /opt/hgsdlc/backups/postgres)"
BACKUP_RETENTION_DAYS="$(read_env BACKUP_RETENTION_DAYS 14)"

mkdir -p "${BACKUP_DIR}"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="${BACKUP_DIR}/postgres-${POSTGRES_DB}-${TIMESTAMP}.sql.gz"

echo "==> Creating PostgreSQL backup: ${BACKUP_FILE}"
compose exec -T postgres pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" | gzip -c > "${BACKUP_FILE}"

if [ ! -s "${BACKUP_FILE}" ]; then
  echo "ERROR: backup file is empty: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "==> Backup completed"
ls -lh "${BACKUP_FILE}"

echo "==> Remove backups older than ${BACKUP_RETENTION_DAYS} days"
find "${BACKUP_DIR}" -type f -name 'postgres-*.sql.gz' -mtime +"${BACKUP_RETENTION_DAYS}" -print -delete || true

echo "Done"
