#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
OUT_DIR="${2:-/Users/nick/IdeaProjects/human-guided-development/tmp/catalog-export}"
USERNAME="${CATALOG_EXPORT_USER:-admin}"
PASSWORD="${CATALOG_EXPORT_PASSWORD:-admin}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

api_post_json() {
  local url="$1"
  local json="$2"
  curl -fsS -X POST \
    -H "Content-Type: application/json" \
    -d "$json" \
    "$url"
}

api_get_auth() {
  local url="$1"
  curl -fsS \
    -H "Authorization: Bearer ${TOKEN}" \
    "$url"
}

safe_write() {
  local path="$1"
  mkdir -p "$(dirname "$path")"
  cat > "$path"
}

yaml_scalar() {
  local value="$1"
  if [[ -z "$value" ]]; then
    echo "null"
    return
  fi
  printf "%s" "$value" | sed "s/'/''/g; s/.*/'&'/"
}

build_tags_yaml() {
  local json="$1"
  echo "$json" | jq -c '[.[] | tostring]'
}

write_rule() {
  local item="$1"
  local rule_id version dir markdown metadata tags_yaml
  rule_id="$(echo "$item" | jq -r '.rule_id')"
  version="$(echo "$item" | jq -r '.version')"
  dir="${OUT_DIR}/rules/${rule_id}/${version}"
  markdown="${dir}/RULE.md"
  metadata="${dir}/metadata.yaml"

  echo "$item" | jq -r '.rule_markdown // ""' | safe_write "$markdown"
  tags_yaml="$(build_tags_yaml "$(echo "$item" | jq -c '.tags // []')")"

  cat > "$metadata" <<EOF
entity_type: rule
id: $(echo "$item" | jq -r '.rule_id')
version: $(echo "$item" | jq -r '.version')
canonical_name: $(echo "$item" | jq -r '.canonical_name')
display_name: $(echo "$item" | jq -r '.title')
description: $(yaml_scalar "$(echo "$item" | jq -r '.description // ""')")
coding_agent: $(echo "$item" | jq -r '.coding_agent')
team_code: $(yaml_scalar "$(echo "$item" | jq -r '.team_code // ""')")
platform_code: $(yaml_scalar "$(echo "$item" | jq -r '.platform_code // ""')")
tags: ${tags_yaml}
rule_kind: $(yaml_scalar "$(echo "$item" | jq -r '.rule_kind // ""')")
scope: $(yaml_scalar "$(echo "$item" | jq -r '.scope // ""')")
environment: $(yaml_scalar "$(echo "$item" | jq -r '.environment // ""')")
lifecycle_status: $(yaml_scalar "$(echo "$item" | jq -r '.lifecycle_status // ""')")
source_ref: $(yaml_scalar "$(echo "$item" | jq -r '.source_ref // ""')")
source_path: $(yaml_scalar "$(echo "$item" | jq -r '.source_path // ""')")
checksum: $(yaml_scalar "$(echo "$item" | jq -r '.checksum // ""')")
approved_by: null
approved_at: null
published_at: $(yaml_scalar "$(echo "$item" | jq -r '.published_at // ""')")
created_at: null
updated_at: $(yaml_scalar "$(echo "$item" | jq -r '.saved_at // ""')")
EOF
}

write_skill() {
  local item="$1"
  local skill_id version dir markdown metadata tags_yaml
  skill_id="$(echo "$item" | jq -r '.skill_id')"
  version="$(echo "$item" | jq -r '.version')"
  dir="${OUT_DIR}/skills/${skill_id}/${version}"
  markdown="${dir}/SKILL.md"
  metadata="${dir}/metadata.yaml"

  echo "$item" | jq -r '.skill_markdown // ""' | safe_write "$markdown"
  tags_yaml="$(build_tags_yaml "$(echo "$item" | jq -c '.tags // []')")"

  cat > "$metadata" <<EOF
entity_type: skill
id: $(echo "$item" | jq -r '.skill_id')
version: $(echo "$item" | jq -r '.version')
canonical_name: $(echo "$item" | jq -r '.canonical_name')
display_name: $(echo "$item" | jq -r '.name')
description: $(yaml_scalar "$(echo "$item" | jq -r '.description // ""')")
coding_agent: $(echo "$item" | jq -r '.coding_agent')
team_code: $(yaml_scalar "$(echo "$item" | jq -r '.team_code // ""')")
platform_code: $(yaml_scalar "$(echo "$item" | jq -r '.platform_code // ""')")
tags: ${tags_yaml}
skill_kind: $(yaml_scalar "$(echo "$item" | jq -r '.skill_kind // ""')")
environment: $(yaml_scalar "$(echo "$item" | jq -r '.environment // ""')")
lifecycle_status: $(yaml_scalar "$(echo "$item" | jq -r '.lifecycle_status // ""')")
source_ref: $(yaml_scalar "$(echo "$item" | jq -r '.source_ref // ""')")
source_path: $(yaml_scalar "$(echo "$item" | jq -r '.source_path // ""')")
checksum: $(yaml_scalar "$(echo "$item" | jq -r '.checksum // ""')")
approved_by: null
approved_at: null
published_at: $(yaml_scalar "$(echo "$item" | jq -r '.published_at // ""')")
created_at: null
updated_at: $(yaml_scalar "$(echo "$item" | jq -r '.saved_at // ""')")
EOF
}

write_flow() {
  local item="$1"
  local flow_id version dir flow_file metadata tags_yaml
  flow_id="$(echo "$item" | jq -r '.flow_id')"
  version="$(echo "$item" | jq -r '.version')"
  dir="${OUT_DIR}/flows/${flow_id}/${version}"
  flow_file="${dir}/FLOW.yaml"
  metadata="${dir}/metadata.yaml"

  echo "$item" | jq -r '.flow_yaml // ""' | safe_write "$flow_file"
  tags_yaml="$(build_tags_yaml "$(echo "$item" | jq -c '.tags // []')")"

  cat > "$metadata" <<EOF
entity_type: flow
id: $(echo "$item" | jq -r '.flow_id')
version: $(echo "$item" | jq -r '.version')
canonical_name: $(echo "$item" | jq -r '.canonical_name')
display_name: $(echo "$item" | jq -r '.title')
description: $(yaml_scalar "$(echo "$item" | jq -r '.description // ""')")
coding_agent: $(echo "$item" | jq -r '.coding_agent')
team_code: $(yaml_scalar "$(echo "$item" | jq -r '.team_code // ""')")
platform_code: $(yaml_scalar "$(echo "$item" | jq -r '.platform_code // ""')")
tags: ${tags_yaml}
flow_kind: $(yaml_scalar "$(echo "$item" | jq -r '.flow_kind // ""')")
risk_level: $(yaml_scalar "$(echo "$item" | jq -r '.risk_level // ""')")
environment: $(yaml_scalar "$(echo "$item" | jq -r '.environment // ""')")
lifecycle_status: $(yaml_scalar "$(echo "$item" | jq -r '.lifecycle_status // ""')")
source_ref: $(yaml_scalar "$(echo "$item" | jq -r '.source_ref // ""')")
source_path: $(yaml_scalar "$(echo "$item" | jq -r '.source_path // ""')")
checksum: $(yaml_scalar "$(echo "$item" | jq -r '.checksum // ""')")
approved_by: null
approved_at: null
published_at: $(yaml_scalar "$(echo "$item" | jq -r '.published_at // ""')")
created_at: null
updated_at: $(yaml_scalar "$(echo "$item" | jq -r '.saved_at // ""')")
EOF
}

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"/{rules,skills,flows}

echo "export from ${BASE_URL} -> ${OUT_DIR}"
LOGIN_PAYLOAD="$(jq -nc --arg u "${USERNAME}" --arg p "${PASSWORD}" '{username:$u,password:$p}')"
TOKEN="$(api_post_json "${BASE_URL}/api/auth/login" "${LOGIN_PAYLOAD}" | jq -r '.token')"
if [[ -z "${TOKEN}" || "${TOKEN}" == "null" ]]; then
  echo "failed to obtain auth token" >&2
  exit 1
fi

RULE_IDS="$(api_get_auth "${BASE_URL}/api/rules" | jq -r '.[].rule_id' | sort -u)"
for rule_id in ${RULE_IDS}; do
  versions_json="$(api_get_auth "${BASE_URL}/api/rules/${rule_id}/versions")"
  while IFS= read -r version; do
    [[ -z "${version}" ]] && continue
    rule_json="$(api_get_auth "${BASE_URL}/api/rules/${rule_id}/versions/${version}")"
    write_rule "${rule_json}"
  done < <(echo "${versions_json}" | jq -r '.[].version')
done

SKILL_IDS="$(api_get_auth "${BASE_URL}/api/skills" | jq -r '.[].skill_id' | sort -u)"
for skill_id in ${SKILL_IDS}; do
  versions_json="$(api_get_auth "${BASE_URL}/api/skills/${skill_id}/versions")"
  while IFS= read -r version; do
    [[ -z "${version}" ]] && continue
    skill_json="$(api_get_auth "${BASE_URL}/api/skills/${skill_id}/versions/${version}")"
    write_skill "${skill_json}"
  done < <(echo "${versions_json}" | jq -r '.[].version')
done

FLOW_IDS="$(api_get_auth "${BASE_URL}/api/flows" | jq -r '.[].flow_id' | sort -u)"
for flow_id in ${FLOW_IDS}; do
  versions_json="$(api_get_auth "${BASE_URL}/api/flows/${flow_id}/versions")"
  while IFS= read -r version; do
    [[ -z "${version}" ]] && continue
    flow_json="$(api_get_auth "${BASE_URL}/api/flows/${flow_id}/versions/${version}")"
    write_flow "${flow_json}"
  done < <(echo "${versions_json}" | jq -r '.[].version')
done

echo "done"
find "${OUT_DIR}" -type f | sort
