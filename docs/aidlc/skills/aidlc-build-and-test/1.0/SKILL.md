---
name: AIDLC Build and Test
description: >
  Analyse all generated units and produce comprehensive build and test instruction
  files: build-instructions.md, unit-test-instructions.md,
  integration-test-instructions.md, and build-and-test-summary.md.
  Covers unit tests, integration tests, performance tests (if applicable),
  and any applicable contract or security tests.
---

# AIDLC Build and Test

## Goal
Produce clear, runnable build and test instructions so any developer (or CI/CD
pipeline) can build all units and validate the implementation without prior
knowledge of the project.

---

## Step 1 — Analyse Testing Scope

Read:
- `aidlc-docs/construction/plans/` — all code generation plans (to understand what was built)
- `aidlc-docs/construction/{unit-name}/nfr-requirements/nfr-requirements.md` (for each unit)
- `aidlc-docs/inception/application-design/unit-of-work.md` (unit list, if available)
- Inspect the workspace root to determine build system and project structure

Determine required test types:
| Type | When to Include |
|------|----------------|
| Unit tests | Always |
| Integration tests | Always (multiple components / units) |
| Performance tests | When NFR specifies response time / throughput targets |
| Contract tests | When microservices communicate via defined APIs |
| Security tests | When NFR specifies compliance requirements |
| E2E tests | When user journeys span multiple services |

---

## Step 2 — Generate Build Instructions

Create `aidlc-docs/construction/build-and-test/build-instructions.md`:

```markdown
# Build Instructions

## Prerequisites
- **Build Tool**: [tool and version]
- **Runtime**: [JDK / Node / Python version]
- **Required Services**: [database, message broker — specify versions]
- **Environment Variables**: [list with descriptions]

## Build Steps

### 1. Install Dependencies
```bash
[command]
```

### 2. Configure Environment
```bash
[environment setup commands]
```

### 3. Build All Units
```bash
[build command for each unit or all together]
```

### 4. Verify Build Success
- **Expected output**: [description of successful build]
- **Artifacts location**: [where built artifacts land]

## Troubleshooting
### Dependency Errors
[cause and resolution]
### Compilation Errors
[cause and resolution]
```

---

## Step 3 — Generate Unit Test Instructions

Create `aidlc-docs/construction/build-and-test/unit-test-instructions.md`:

```markdown
# Unit Test Instructions

## Run All Unit Tests
```bash
[command]
```

## Per-Unit Test Execution
### [unit-name]
```bash
[command to run tests for this unit only]
```

## Expected Results
- **Total tests**: [estimated count]
- **Coverage target**: [% from NFR]
- **Report location**: [path]

## Fixing Failures
1. Check output in [location]
2. Identify failing test
3. Fix code, rerun until green
```

---

## Step 4 — Generate Integration Test Instructions

Create `aidlc-docs/construction/build-and-test/integration-test-instructions.md`:

```markdown
# Integration Test Instructions

## Purpose
[What cross-unit or cross-layer interactions are being tested]

## Setup
```bash
[start required services: docker-compose up, etc.]
```

## Test Scenarios
### Scenario 1: [Unit A] → [Unit B]
- **What**: [description]
- **Setup**: [specific config or data setup]
- **Run**: [command]
- **Expected**: [outcome]

## Run All Integration Tests
```bash
[command]
```

## Teardown
```bash
[cleanup commands]
```
```

---

## Step 5 — Generate Performance Test Instructions (If Applicable)

Create `aidlc-docs/construction/build-and-test/performance-test-instructions.md` only if
NFR requirements specify performance targets:

- Setup instructions
- Load test commands (JMeter, k6, Gatling)
- Stress test commands
- Acceptance thresholds (P95, throughput, error rate)
- How to interpret and act on results

---

## Step 6 — Generate Summary

Create `aidlc-docs/construction/build-and-test/build-and-test-summary.md`:

```markdown
# Build and Test Summary

## Build Status
- **Tool**: [name]
- **Command**: [command]
- **Artifacts**: [list and locations]

## Test Coverage
| Type | Command | Expected Result |
|------|---------|----------------|
| Unit | [cmd] | All pass, ≥X% coverage |
| Integration | [cmd] | All scenarios pass |
| Performance | [cmd or N/A] | P95 < Xms |
| Contract | [cmd or N/A] | All contracts valid |
| Security | [cmd or N/A] | No critical vulnerabilities |

## Ready for Next Phase
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Coverage threshold met
- [ ] No new compilation warnings
- [ ] Performance targets met (if applicable)
```

---

## Constraints

- Instructions must be runnable as-is on a clean checkout.
- Use the exact build tool and commands detected from the workspace/NFR artifacts.
- Do not write new test code — these are instructions for running tests already
  generated during code generation.
- Mark inapplicable test types clearly as N/A with a brief reason.
