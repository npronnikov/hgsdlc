---
name: AIDLC User Stories — Generate
description: >
  Read the answered story-questions.md planning document and generate a complete
  set of INVEST-compliant user stories (stories.md) and a user personas catalogue
  (personas.md), with acceptance criteria per story.
---

# AIDLC User Stories — Generate

## Goal
Produce a concrete, team-aligned user story set that maps directly to the approved
requirements and can serve as the specification for implementation and testing.

---

## Step 1 — Load and Validate Answers

1. Read `aidlc-docs/inception/plans/story-questions.md`.
2. Confirm every `[Answer]:` tag is filled; note any vague or contradictory answers.
3. Read `aidlc-docs/inception/requirements/requirements.md` for functional scope.
4. Check for any unanswered ambiguities — if found, list them as risks in the output.

---

## Step 2 — Define Personas

For each distinct user type identified in the answers:
- Name / role
- Goals and motivations
- Key characteristics
- Frustrations / pain points

Save in `aidlc-docs/inception/user-stories/personas.md`.

---

## Step 3 — Generate User Stories

Apply the INVEST criteria for every story:
- **Independent**: can be built without depending on another story being done first
- **Negotiable**: scope can be discussed
- **Valuable**: delivers benefit to a user or business
- **Estimable**: small enough to estimate
- **Small**: completable in one sprint
- **Testable**: has verifiable acceptance criteria

Use the breakdown approach from the answers (journey-based / feature-based / persona-based / domain-based).

---

## Output Format — stories.md

Save as `aidlc-docs/inception/user-stories/stories.md`:

```markdown
# User Stories

## Epic: [Epic Name]

### US-001: [Story title]
**As a** [persona]
**I want to** [action / goal]
**So that** [benefit / value]

**Acceptance Criteria**:
- [ ] Given [context], when [action], then [expected result]
- [ ] Given [context], when [error scenario], then [error handling]

**Story Points**: [estimate if provided]
**Priority**: [High / Medium / Low]
**Linked Requirements**: FR-1, API-1

---

### US-002: ...
```

---

## Output Format — personas.md

Save as `aidlc-docs/inception/user-stories/personas.md`:

```markdown
# User Personas

## [Persona Name]
- **Role**: [title / role]
- **Goals**: [what they want to achieve]
- **Motivations**: [why they use the system]
- **Pain Points**: [current frustrations]
- **Technical Proficiency**: [Low / Medium / High]

**Relevant Stories**: US-001, US-003
```

---

## Constraints

- Every story must be traceable to at least one requirement (FR-*, API-*, etc.).
- Acceptance criteria must be written as testable Given/When/Then statements.
- Do not create stories for requirements that are purely technical (migrations, refactors)
  unless they have user-visible impact.
- Follow the format and breakdown approach specified in the planning answers exactly.
- Do not invent personas or story dimensions not grounded in the answers and requirements.
