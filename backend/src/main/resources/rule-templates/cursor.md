---
# Field: description
# Why: helps the agent understand when the rule is relevant.
# Example:
# Rules for a TypeScript backend API: DTOs, validation, error handling,
# and handler/service/repository structure.
description: >-
  <when and for which tasks this rule should be applied>

# Field: globs
# Why: scope the rule to specific files/directories.
# Example: "src/api/**/*.ts"
globs: "<glob-pattern>"

# Field: alwaysApply
# Why:
# - true  = rule is loaded in all chats;
# - false = rule is loaded by relevance/matching.
# Example: false
alwaysApply: <true|false>
---

# <Rule name>

## Context
<!-- Why: quickly explain the scope of this rule. -->
<!-- Example:
This rule applies to the backend API layer and all files under src/api/.
-->

## Mandatory requirements
<!-- Why: define short, verifiable requirements. -->
<!-- Example:
- Every endpoint must validate input.
- Errors must use a unified ErrorResponse format.
- Business logic must not live in controllers.
-->

## Change structure
<!-- Why: lock preferred architecture for changes. -->
<!-- Example:
- controller = HTTP layer only;
- service = business logic;
- repository = data access.
-->

## Prohibited
<!-- Why: explicitly call out anti-patterns. -->
<!-- Example:
- Do not write SQL directly in controllers.
- Do not duplicate error schemas across endpoints.
-->

## Examples
<!-- Why: reduce ambiguity. -->
<!-- Example:
Good:
- controller calls service and returns typed response.

Bad:
- controller queries DB directly and builds response manually.
-->
