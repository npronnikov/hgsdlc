#!/usr/bin/env bash
set -euo pipefail

QWEN_BIN="${QWEN_BIN:-qwen}"
QWEN_SETTINGS_PATH="${QWEN_SETTINGS_PATH:-${HOME}/.qwen/settings.json}"

if ! command -v "${QWEN_BIN}" >/dev/null 2>&1; then
  echo "ERROR: qwen binary not found in PATH (QWEN_BIN=${QWEN_BIN})" >&2
  exit 1
fi

if [ ! -f "${QWEN_SETTINGS_PATH}" ]; then
  echo "ERROR: qwen settings.json not found at ${QWEN_SETTINGS_PATH}" >&2
  echo "Mount settings read-only from VM, for example: /opt/hgsdlc/secrets/qwen-settings.json:${QWEN_SETTINGS_PATH}:ro" >&2
  exit 1
fi

echo "qwen version: $(${QWEN_BIN} --version | head -n 1)"
exec java ${JAVA_OPTS:-} -jar /app/app.jar
