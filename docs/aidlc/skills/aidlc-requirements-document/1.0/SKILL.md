---
name: AIDLC Requirements Document
description: >
  Read the answered clarification questions and produce a complete, structured
  requirements.md covering functional requirements, API requirements, data model
  requirements, authorization, testing, constraints, and acceptance criteria.
  Each requirement is concrete, verifiable, and tied to specific files or classes.
---

# AIDLC Requirements Document

## Goal
Transform user answers into actionable, verifiable requirements that developers
and reviewers can use as the single source of truth for the entire implementation.

---

## Step 1 — Load and Validate Answers

1. Read `aidlc-docs/inception/requirements/requirement-questions.md`.
2. Confirm every `[Answer]:` tag has a non-empty answer.
3. Check for vague or contradictory answers (e.g., "depends", "not sure", "maybe").
   - If found: list the ambiguities in the output and flag them as risks.
4. Load reverse engineering context (brownfield): architecture.md, technology-stack.md,
   code-structure.md — use these to identify the exact files/classes to reference.

---

## Step 2 — Synthesize Requirements

For each requirement, ensure it is:
- **Specific**: unambiguous about what to build
- **Verifiable**: can be validated by a test or manual check
- **Located**: references the exact class, method, table, or endpoint
- **Atomic**: one change per requirement

---

## Output Format

Save as `aidlc-docs/inception/requirements/requirements.md`:

```markdown
# Requirements

## Request Context
[1 paragraph: what is requested and which parts of the system are affected]

## Request Classification
- **Type**: [New Feature / Bug Fix / etc.]
- **Scope**: [Single Component / Multiple Components / etc.]
- **Complexity**: [Trivial / Simple / Moderate / Complex]
- **Risk Level**: [Low / Medium / High / Critical]

## Affected Files
- `path/to/FileA.java` — [what changes: add field / modify method / new class]
- `path/to/FileB.java` — [what changes]
- `db/changelog/NNN-description.sql` — [schema migration]
- `path/to/FileATest.java` — [new tests]

---

## Functional Requirements

### FR-1: [Title]
**What**: [concrete behaviour]
**Where**: `ClassName#methodName` or `table.column`
**Rule**: [business rule, validation, or constraint]
**Example**: [concrete example of correct/incorrect input or outcome]

### FR-2: ...

---

## API Requirements

### API-1: [Endpoint title]
**Method & URL**: `POST /api/resource`
**Request**: [fields, types, required/optional]
**Response (success)**: `201 Created` — [body description]
**Response (error)**: `400 Bad Request` when [condition]
**Authorization**: [role / access condition]

---

## Data Model Requirements

### DM-1: [Change title]
**Table**: `table_name`
**Change**: [add column `name TYPE NOT NULL DEFAULT '...'` / rename / drop]
**Migration**: Liquibase changeset required
**Backward compatibility**: [Yes / No — rationale]

---

## Authorization Requirements

### AUTH-1: [Requirement title]
**Allowed**: [role or condition]
**Denied**: [role or condition]
**Where to enforce**: `ServiceClass#method` or controller annotation

---

## Testing Requirements

### TEST-1: [What to cover]
**Test type**: unit / @WebMvcTest / @DataJpaTest / integration
**Scenarios**:
- [happy path]
- [edge case 1]
- [edge case 2]
**Location**: `SomeServiceTest.java`

---

## Constraints and Risks

- [Constraint 1]: [why and how to handle]
- [Risk 1]: [description and mitigation]

---

## Acceptance Criteria

- [ ] [Verifiable criterion 1]
- [ ] [Verifiable criterion 2]
- [ ] All tests pass
- [ ] No regressions in existing tests
```

---

## Constraints

- Do NOT write code — only describe what needs to be done.
- Reference only files that actually exist in the codebase.
- If two answers contradict each other, surface it explicitly as a risk.
- Do not add requirements that do not follow from the user's request.
- Keep each FR, API-*, DM-*, AUTH-*, TEST-* entry numbered sequentially.
