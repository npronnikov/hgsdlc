---
# Field: name
# Why: machine-readable skill name; usually used as folder id and short slug.
# Example: api-contract-review
name: <skill-name-in-kebab-case>

# Field: description
# Why: helps the agent understand WHAT this skill does and WHEN to use it.
# Example:
# Reviews API contracts and HTTP schemas. Use when reviewing endpoints,
# OpenAPI specs, request/response DTOs, and API backward compatibility.
description: >-
  <briefly describe what the skill does and when it should be used>

# Field: license
# Why: specify the license if this skill is shared between teams or repositories.
# Example: Proprietary. See LICENSE.txt
license: <optional>

# Field: compatibility
# Why: declare environment requirements in advance: product, shell access, required utilities, etc.
# Example: Cursor; requires bash, jq, and access to project OpenAPI files.
compatibility: <optional>
---

# <Human-readable skill name>

## When to use
<!-- Why: list triggers that indicate this skill is relevant. -->
<!-- Example:
- When user asks to review an API contract.
- When OpenAPI must be compared before/after changes.
- When finding breaking changes in request/response schemas.
-->

## What to do
<!-- Why: provide a reproducible step-by-step workflow. -->
<!-- Example:
1. Locate OpenAPI specs, DTOs, and controllers.
2. Map endpoints to request/response schemas.
3. Find incompatible changes.
4. Build report: critical / warning / note.
-->

## Constraints
<!-- Why: define skill boundaries. -->
<!-- Example:
- Do not invent endpoints that are not in code/specs.
- If a spec is outdated, explicitly mention it.
-->

## Result format
<!-- Why: normalize output. -->
<!-- Example:
Return:
1. brief summary;
2. list of breaking changes;
3. list of recommendations;
4. list of files to update.
-->

## Examples
<!-- Why: show 2-3 typical requests. -->
<!-- Example:
- "Check whether we broke public REST API"
- "Compare new OpenAPI schema with the previous one"
-->
