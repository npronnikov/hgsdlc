# Project: <project name>
<!-- Why: provide immediate domain context to the model. -->
<!-- Example: B2B SaaS platform for contract management. -->

## General instructions
<!-- Why: top-level rules for all responses and edits. -->
<!-- Example:
- Follow the existing project style.
- Do not change public APIs without explicit request.
- Propose minimal changes before large refactors.
-->

## Stack and versions
<!-- Why: prevent incompatible suggestions from Qwen. -->
<!-- Example:
- Node.js 22
- TypeScript 5.7
- React 19
- PostgreSQL 16
-->

## Code style
<!-- Why: define coding conventions. -->
<!-- Example:
- Indentation: 2 spaces
- Always use strict equality
- Prefer named exports
- All new public functions must include JSDoc
-->

## Architectural rules
<!-- Why: define preferred patterns. -->
<!-- Example:
- UI logic lives in src/features/
- API client only in src/shared/api/
- No direct feature-to-feature imports, only through module public API
-->

## Testing rules
<!-- Why: ensure model knows the test workflow. -->
<!-- Example:
- Unit tests: Vitest
- E2E: Playwright
- For bugfixes, add a test that fails before the fix
-->

## Dependency rules
<!-- Why: limit uncontrolled package additions. -->
<!-- Example:
- Do not add dependencies unless necessary
- If a dependency is needed, suggest a no-dependency alternative first
-->

## Project commands
<!-- Why: provide ready-to-run commands for Qwen. -->
<!-- Example:
- dev: pnpm dev
- test: pnpm test
- lint: pnpm lint
- build: pnpm build
-->

## Constraints and security
<!-- Why: capture strict boundaries. -->
<!-- Example:
- Do not print secrets in logs
- Do not disable validation to pass tests
- Do not rewrite migrations after release
-->

## Important files and directories
<!-- Why: speed up project navigation. -->
<!-- Example:
- src/app/ — bootstrap
- src/features/ — business features
- docs/architecture.md — architecture decisions
-->

## Local context imports
<!-- Why: allow modular inclusion of additional markdown files. -->
<!-- Example:
@docs/architecture.md
@docs/coding-standards.md
-->
