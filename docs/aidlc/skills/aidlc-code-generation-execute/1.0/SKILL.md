---
name: AIDLC Code Generation — Execute
description: >
  Read the approved code-generation-plan.md for the current unit and execute it
  step by step: create or modify source files, write tests, apply schema
  migrations, and mark each plan checkbox [x] immediately upon completion.
---

# AIDLC Code Generation — Execute

## Goal
Implement all code for the unit exactly as specified in the plan — no deviations,
no additions, no skipped steps. Every change must be verifiable against the plan.

---

## Step 1 — Load the Plan

Read `aidlc-docs/construction/plans/{unit-name}-code-generation-plan.md`.
- Find the first unchecked `[ ]` step — that is the current step.
- Do NOT start from the beginning if some steps are already `[x]`.

---

## Step 2 — Execute the Current Step

For each unchecked step:

1. **Verify target path** — confirm it is under the workspace root, never `aidlc-docs/`.
2. **Brownfield — check file existence**:
   - File exists → **modify in-place**. Never create a copy (`_modified`, `_new`, `_v2`).
   - File does not exist → create it.
3. **Follow the plan description exactly**:
   - Implement only what the step specifies.
   - Write tests for the step's changes if the step says to.
   - Do not introduce features not in the plan.
4. **Automation-friendly UI code** (if generating frontend):
   - Add `data-testid` to all interactive elements using format `{component}-{role}`.
   - Use stable, predictable IDs (not dynamic/random).

---

## Step 3 — Verify After Each Step

After completing a step, run the appropriate verification command from the plan:
- Schema steps: apply Liquibase / run `./gradlew compileJava`
- Code steps: `./gradlew compileJava` or equivalent build compile
- Test steps: `./gradlew test` or equivalent

If verification fails, fix the issue before marking the step complete.

---

## Step 4 — Mark Progress

Immediately after completing and verifying a step:
- Change `[ ]` to `[x]` in `aidlc-docs/construction/plans/{unit-name}-code-generation-plan.md`

---

## Step 5 — Repeat

Return to Step 1 (find next unchecked step) until all steps are `[x]`.

---

## Step 6 — Final Verification

When all steps are complete:
- Run the full build: `./gradlew build` (or equivalent)
- Run all tests: `./gradlew test` (or equivalent)
- Confirm no new compiler warnings were introduced
- Confirm no duplicate files were created

---

## Constraints

- **NEVER** deviate from the plan — if the plan is wrong, stop and report the issue;
  do not improvise a fix.
- **NEVER** create files in `aidlc-docs/` — documentation summaries only.
- **NEVER** create renamed copies of existing files.
- **ALWAYS** mark `[x]` in the same step where work is done — not in bulk at the end.
- Follow all rules from connected rule refs (coding standards, naming conventions).
- Do not generate more code than what the plan steps describe.
