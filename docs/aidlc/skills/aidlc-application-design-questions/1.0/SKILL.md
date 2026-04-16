---
name: AIDLC Application Design — Questions
description: >
  Review requirements and user stories to generate design-clarification questions
  covering component boundaries, method interfaces, service layer design, and
  dependency patterns. Produces design-questions.md with [Answer]: tags.
---

# AIDLC Application Design — Questions

## Goal
Collect the team's architectural decisions before designing components, so that
the generated design reflects deliberate choices rather than AI assumptions.

---

## Step 1 — Load Context

Read:
- `aidlc-docs/inception/requirements/requirements.md`
- `aidlc-docs/inception/user-stories/stories.md` (if available)
- Reverse engineering artifacts (if brownfield): architecture.md, code-structure.md

---

## Step 2 — Identify Design Decisions to Clarify

Evaluate ALL categories; generate questions for any with ambiguity:

- **Component Identification**: how many components, how to group responsibilities,
  what are the natural boundaries
- **Component Methods**: key method signatures, input/output expectations, interface
  contracts (detailed business rules come later in Functional Design)
- **Service Layer**: orchestration pattern, how services coordinate components,
  transaction boundaries
- **Component Dependencies**: communication patterns (sync/async), coupling concerns,
  shared state
- **Design Patterns**: architectural style preference (layered, hexagonal, event-driven),
  pattern constraints from existing codebase

---

## Output Format

Save as `design-questions.md`:

```markdown
# Application Design Questions

## Design Scope
- **New Components Needed**: [list inferred from requirements]
- **Existing Components to Modify**: [list from RE artifacts]

---

## Q1: [Question title]
**Category**: Component Identification / Service Layer / etc.
**Context**: [design decision this drives]
**Options**:
A) [Option A]
B) [Option B]
C) [Option C]
X) Other (describe after [Answer]:)

[Answer]:

---

## Q2: ...
```

**Rules**: A/B/C options, always include X) Other, one `[Answer]:` per question.
Minimum 5 questions.

---

## Constraints

- Do NOT generate design artifacts — only clarification questions.
- Focus on structural decisions, not detailed business logic (that comes in Functional Design).
- Reference actual files/classes from reverse engineering when relevant.
