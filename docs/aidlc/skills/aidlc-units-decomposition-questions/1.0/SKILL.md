---
name: AIDLC Units Decomposition — Questions
description: >
  Review the application design and requirements to generate decomposition-planning
  questions covering story grouping, unit boundaries, inter-unit dependencies,
  and deployment model. Produces units-questions.md with [Answer]: tags.
---

# AIDLC Units Decomposition — Questions

## Goal
Collect explicit team decisions on how to decompose the system into units of work
before generating unit boundaries, ensuring alignment on deployment model,
team ownership, and integration strategy.

A unit of work = a logical grouping of stories for development purposes.
- Microservices: each unit becomes an independently deployable service.
- Monolith: the single unit represents the whole app with logical modules.

---

## Step 1 — Load Context

Read:
- `aidlc-docs/inception/application-design/application-design.md`
- `aidlc-docs/inception/requirements/requirements.md`
- `aidlc-docs/inception/user-stories/stories.md` (if available)
- Reverse engineering artifacts (if brownfield)

---

## Step 2 — Identify Decomposition Decisions to Clarify

Evaluate ALL categories; generate questions for each with ambiguity:

- **Story Grouping**: natural affinity groups, how stories cluster
- **Unit Boundaries**: what defines a unit boundary — domain, deployment, team ownership
- **Dependencies**: how units communicate, shared resources, integration patterns
- **Deployment Model**: independently deployable services vs. monolith modules
- **Team Alignment**: ownership per unit, team structure, handoff points
- **Technical Constraints**: per-unit scaling requirements, technology divergence
- **Business Domain**: bounded contexts, domain events, ubiquitous language per unit

---

## Output Format

Save as `aidlc-docs/inception/plans/units-questions.md`:

```markdown
# Units Decomposition Questions

## Candidate Units (Inferred)
- [Unit name]: [components it would include, stories it would cover]

---

## Q1: [Question title]
**Category**: Story Grouping / Deployment Model / etc.
**Context**: [decomposition decision this drives]
**Options**:
A) [Option A]
B) [Option B]
C) [Option C]
X) Other (describe after [Answer]:)

[Answer]:

---

## Q2: ...
```

Rules: A/B/C options, X) Other always present, one `[Answer]:` per question, min 5 questions.

---

## Constraints

- Do NOT generate unit artifacts — only decomposition questions.
- Keep questions focused on unit boundaries and deployment decisions.
- Reference actual components from application-design.md.
