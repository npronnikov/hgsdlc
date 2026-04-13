---
name: AIDLC User Stories — Planning Questions
description: >
  Review the approved requirements and generate a story-planning question file
  that covers user personas, story granularity, acceptance criteria format,
  and breakdown approach. Produces story-questions.md with [Answer]: tags
  so the team can guide user-story generation precisely.
---

# AIDLC User Stories — Planning Questions

## Goal
Before generating user stories, collect the team's decisions on how stories should
be structured, what personas to use, and how acceptance criteria should be written.
This prevents generating stories that must be completely rewritten.

---

## Step 1 — Load Context

Read:
- `aidlc-docs/inception/requirements/requirements.md`
- Reverse engineering artifacts (if brownfield)

Identify:
- The personas implied by the requirements (who uses this system?)
- The major feature areas that stories will cover
- Any user-journey complexity that warrants breakdown questions

---

## Step 2 — Assess Story Need

User stories add clear value when ANY of the following are true:
- New user-facing features or changes to user workflows
- Multiple user types or personas involved
- Complex acceptance criteria needed
- Customer-facing API or service changes
- Cross-team collaboration required

If none of these apply (pure internal refactoring, isolated bug fix, infra-only),
note this in the output and recommend skipping user stories.

---

## Step 3 — Generate Planning Questions

Evaluate ALL categories below and generate questions for each with any ambiguity:

- **User Personas**: who are the actors? roles, characteristics, motivations
- **Story Granularity**: size, level of detail, how to break epics into stories
- **Story Format**: template preference (standard "As a…" / job story / other)
- **Breakdown Approach**: by user journey / by feature / by persona / by domain
- **Acceptance Criteria**: format (Given/When/Then / checklist), detail level, testing approach
- **User Journeys**: key workflows, happy paths, error paths
- **Business Context**: success metrics, stakeholder expectations
- **Technical Constraints**: integration requirements, known limitations

---

## Output Format

Save as `aidlc-docs/inception/plans/story-questions.md`:

```markdown
# User Stories Planning Questions

## Story Need Assessment
- **Recommended**: [Execute / Skip with rationale]
- **Reason**: [which indicators triggered execution]

## Personas Identified
- [Persona name]: [inferred from requirements]

---

## Q1: [Question title]
**Category**: User Personas / Story Format / etc.
**Context**: [why this decision matters]
**Options**:
A) [Option A]
B) [Option B]
C) [Option C]
X) Other (describe after [Answer]:)

[Answer]:

---

### Q2: ...
```

**Rules for questions:**
- Label options A, B, C (never numeric); always include X) Other.
- One `[Answer]:` tag per question, on its own line.
- Minimum 5 questions.

---

## Constraints

- Do NOT generate stories yet — only planning questions.
- Do NOT proceed to story generation until all `[Answer]:` tags are filled.
- Keep questions focused on story structure decisions, not technical implementation.
