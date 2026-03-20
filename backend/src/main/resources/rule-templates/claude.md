---
# Field: paths
# Why: scope the rule to specific paths/file types.
# Example:
# - "src/api/**/*.ts"
# - "src/contracts/**/*.ts"
paths:
  - "<glob-1>"
  - "<glob-2>"
---

# <Rule name>

## Purpose
<!-- Why: explain why this rule exists. -->
<!-- Example:
This rule defines standards for the API layer: validation, error handling,
request/response contracts, and OpenAPI annotations.
-->

## Mandatory requirements
<!-- Why: list short, verifiable instructions. -->
<!-- Example:
- Every endpoint validates input.
- All errors are normalized to one format.
- Public contract changes require documentation updates.
-->

## Architecture conventions
<!-- Why: prevent layer mixing in Claude output. -->
<!-- Example:
- handlers only handle HTTP input/output
- services contain business logic
- repositories work with the database
-->

## Code change rules
<!-- Why: define expected workflow. -->
<!-- Example:
- First find an existing project pattern
- Then apply a minimally invasive change
- Add tests when fixing bugs
-->

## What not to do
<!-- Why: explicitly define anti-patterns. -->
<!-- Example:
- Do not embed raw SQL in handlers
- Do not duplicate DTOs across modules without reason
- Do not silently change public APIs
-->

## Report format
<!-- Why: keep Claude responses consistent. -->
<!-- Example:
Return:
1. what you changed;
2. why;
3. which files are affected;
4. which tests are needed.
-->

## Examples
<!-- Why: reduce ambiguity. -->
<!-- Example:
Good:
- "Added validator for POST /users and updated response schema"

Bad:
- "Added business logic for user creation in controller"
-->
