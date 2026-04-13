---
name: AIDLC Code Generation — Plan
description: >
  Analyse all design artifacts for a unit of work and produce a numbered,
  checkbox-driven code-generation-plan.md that lists every file to create or
  modify, every test to write, and every migration to run — in the correct
  dependency order.
---

# AIDLC Code Generation — Plan

## Goal
Produce a deterministic, step-by-step plan that the AI agent can execute
mechanically in the next node — no ambiguity about what to write, where to
write it, or in what order.

---

## Step 1 — Load All Unit Design Artifacts

Read (all must exist before planning):
- `aidlc-docs/inception/application-design/unit-of-work.md` (unit boundary)
- `aidlc-docs/inception/application-design/unit-of-work-story-map.md` (stories)
- `aidlc-docs/construction/{unit-name}/functional-design/domain-entities.md`
- `aidlc-docs/construction/{unit-name}/functional-design/business-rules.md`
- `aidlc-docs/construction/{unit-name}/functional-design/data-flow.md`
- `aidlc-docs/construction/{unit-name}/nfr-requirements/tech-stack-decisions.md`
- `aidlc-docs/inception/requirements/requirements.md` (for DM-* and API-* sections)
- `aidlc-docs/aidlc-state.md` (workspace root, project type)
- Reverse engineering: `code-structure.md` (brownfield — existing file inventory)

---

## Step 2 — Determine Code Location

Per project type:
- **Brownfield**: use existing file structure from code-structure.md
- **Greenfield single unit**: `src/`, `tests/`, `config/` in workspace root
- **Greenfield multi-unit microservices**: `{unit-name}/src/`, `{unit-name}/tests/`
- **Greenfield multi-unit monolith**: `src/{unit-name}/`, `tests/{unit-name}/`

Never use `aidlc-docs/` for application code.

---

## Step 3 — Build the Step Sequence

Order from foundation to surface:

1. **Schema migrations** (Liquibase/Flyway changesets)
2. **Domain entities and value objects**
3. **Repository layer** (interfaces, custom queries)
4. **Service layer** (business logic, orchestration)
5. **API layer** (controllers, DTOs, request/response records)
6. **Unit tests** for each layer (immediately after the layer)
7. **Integration tests** (cross-layer scenarios)
8. **Configuration** (ENV variables, Spring profiles, etc.)
9. **API documentation** (OpenAPI annotations or README updates)
10. **Frontend components** (if applicable, after API is defined)

For brownfield: each step specifies **Modify** or **Create** for each file.

---

## Output Format

Save as `aidlc-docs/construction/plans/{unit-name}-code-generation-plan.md`:

```markdown
# Code Generation Plan — [unit-name]

## Unit Context
- **Stories covered**: US-001, US-002, FR-3
- **Dependencies on other units**: [unit-x API contract / shared model]
- **Workspace root**: [absolute path from aidlc-state.md]
- **Project type**: Brownfield / Greenfield

---

## Step 1 — Schema Migration (complexity: low/medium/high)
**Goal**: [what schema change is needed]

- [ ] [Create/Modify] `db/changelog/043-description.sql`
  - [specific DDL: ADD COLUMN / CREATE TABLE / ALTER TABLE]

**Verification**: Liquibase applies without errors on clean DB

---

## Step 2 — Domain Entities (complexity: ...)
**Goal**: [entities to create or modify]

- [ ] [Create/Modify] `src/main/java/.../domain/EntityName.java`
  - Add field `fieldName: Type`
  - Add relationship `@OneToMany List<Child> children`

**Verification**: `./gradlew compileJava` succeeds

---

## Step 3 — Repository Layer
...

## Step 4 — Service Layer
...

## Step 5 — API Layer
...

## Step 6 — Unit Tests
...

## Step 7 — Integration Tests
...

## Step 8 — Configuration
...

---

## Story Traceability
| Story | Implemented in Steps |
|-------|---------------------|
| US-001 | 2, 4, 5, 6 |
| FR-3 | 1, 2, 3 |

## Risk Notes
- [Risk 1]: [what to watch for during execution]
```

---

## Constraints

- Every step must have at least one checkbox `[ ]`.
- Every file path must be absolute from the workspace root.
- Brownfield: explicitly state **Create** or **Modify** for each file; never create
  copies like `ClassName_modified.java`.
- Do NOT write actual code in this plan — only describe what to do, where, and why.
- The plan is the single source of truth for the execution node; it must be complete
  enough to follow without re-reading design artifacts.
