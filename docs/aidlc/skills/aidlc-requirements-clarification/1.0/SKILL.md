---
name: AIDLC Requirements Clarification
description: >
  Analyze the user's request and the existing codebase (if brownfield), classify
  request type and complexity, then produce a structured clarifying-questions file
  with [Answer]: tags covering all ambiguous areas before requirements are written.
---

# AIDLC Requirements Clarification

## Goal
Surface every ambiguity in the user's request before writing a single requirement.
A thorough question file here prevents costly rework later.

---

## Step 1 — Load Context

If brownfield project, load:
- `aidlc-docs/inception/reverse-engineering/architecture.md`
- `aidlc-docs/inception/reverse-engineering/component-inventory.md`
- `aidlc-docs/inception/reverse-engineering/technology-stack.md`

---

## Step 2 — Classify the Request

Determine and record:

| Dimension | Value |
|-----------|-------|
| **Clarity** | Clear / Vague / Incomplete |
| **Request Type** | New Feature / Bug Fix / Refactoring / Upgrade / Migration / Enhancement / New Project |
| **Scope** | Single File / Single Component / Multiple Components / System-wide / Cross-system |
| **Complexity** | Trivial / Simple / Moderate / Complex |

---

## Step 3 — Identify All Ambiguous Areas

Evaluate EVERY category below. For each one with ANY ambiguity, generate at least
one question:

- **Functional Requirements**: core features, user interactions, system behaviours
- **Non-Functional Requirements**: performance, security, scalability, usability
- **User Scenarios**: use cases, user journeys, edge cases, error scenarios
- **Business Context**: goals, constraints, success criteria, stakeholder needs
- **Technical Context**: integration points, data requirements, system boundaries
- **Quality Attributes**: reliability, maintainability, testability, accessibility

Default rule: **when in doubt, ask** — incomplete requirements lead to poor implementations.

---

## Output Format

Save as `aidlc-docs/inception/requirements/requirement-questions.md`:

```markdown
# Requirements Clarification Questions

## Request Classification
- **Clarity**: [Clear / Vague / Incomplete]
- **Type**: [type]
- **Scope**: [scope]
- **Complexity**: [complexity]

## Summary of Understanding
[2–3 sentences: what you believe the user wants and what the main uncertainties are]

---

## Functional Requirements

### Q1: [Short question title]
**Context**: [Why this question matters]
**Options**:
A) [Option A]
B) [Option B]
C) [Option C]
X) Other (describe after [Answer]:)

[Answer]:

---

### Q2: ...

## Non-Functional Requirements

### Q3: ...

## User Scenarios

### Q4: ...

## Business Context

### Q5: ...

## Technical Context

### Q6: ...
```

**Rules for questions:**
- Label options A, B, C, D (never numeric) — always include an X) Other option.
- Each question must have exactly one `[Answer]:` tag on its own line.
- Never ask rhetorical or leading questions.
- Minimum 5 questions for Simple requests, 10 for Moderate, 15+ for Complex.

---

## Constraints

- Do NOT write requirements yet — only ask questions.
- Do NOT proceed to generate requirements until all `[Answer]:` tags are filled.
- Reference real files/classes from reverse engineering artifacts when relevant.
- If a clarification reveals a contradiction, add a follow-up question.
