---
name: AIDLC Workspace Detection
description: >
  Scan the project workspace to determine brownfield vs greenfield status,
  resume an existing AIDLC session if one exists, and create aidlc-docs/aidlc-state.md
  as the canonical state-tracking file for the entire AIDLC workflow.
---

# AIDLC Workspace Detection

## Goal
Determine the type of workspace (new project vs existing codebase) and bootstrap
the state-tracking file so every subsequent AIDLC stage knows where it stands.

---

## Step 1 — Check for Existing Session

Check whether `aidlc-docs/aidlc-state.md` already exists.

- **Exists**: Load it and report the current stage. Do NOT overwrite it.
  Summarise resumed state to the user and stop — the session will continue from
  the last completed stage.
- **Does not exist**: Proceed to Step 2.

---

## Step 2 — Scan Workspace for Source Code

Scan the workspace root (never `aidlc-docs/`) for source files and build descriptors:

- Source files: `.java`, `.py`, `.js`, `.ts`, `.jsx`, `.tsx`, `.kt`, `.go`, `.rs`,
  `.rb`, `.php`, `.cs`, `.cpp`, `.c`, `.scala`, `.groovy`
- Build files: `pom.xml`, `build.gradle`, `package.json`, `Makefile`, `pyproject.toml`,
  `Cargo.toml`, `go.mod`
- Project structure indicators: `src/`, `lib/`, `app/`, `cmd/`

Record:
- **Existing Code**: Yes / No
- **Programming Languages**: list of detected languages
- **Build System**: Gradle / Maven / npm / Cargo / etc.
- **Project Structure**: Monolith / Microservices / Library / Empty
- **Workspace Root**: absolute path

---

## Step 3 — Determine Project Type and Next Stage

| Condition | Flag | Next AIDLC stage |
|-----------|------|-----------------|
| No source code found | greenfield=true | Requirements Analysis |
| Source code found, no `aidlc-docs/inception/reverse-engineering/` | brownfield=true | Reverse Engineering |
| Source code found, RE artifacts exist and are current | brownfield=true | Requirements Analysis (load RE artifacts) |
| Source code found, RE artifacts stale or user requested rerun | brownfield=true | Reverse Engineering |

---

## Output Format

Create `aidlc-docs/aidlc-state.md` with the following structure:

```markdown
# AI-DLC State Tracking

## Project Information
- **Project Type**: [Greenfield / Brownfield]
- **Start Date**: [ISO 8601 timestamp]
- **Current Stage**: INCEPTION — Workspace Detection

## Workspace State
- **Existing Code**: [Yes / No]
- **Programming Languages**: [list]
- **Build System**: [name]
- **Project Structure**: [type]
- **Workspace Root**: [absolute path]
- **Reverse Engineering Needed**: [Yes / No]

## Code Location Rules
- **Application Code**: workspace root (NEVER in aidlc-docs/)
- **Documentation**: aidlc-docs/ only

## Extension Configuration
| Extension | Enabled | Decided At |
|-----------|---------|------------|

## Stage Progress
### INCEPTION PHASE
- [x] Workspace Detection — [timestamp]
- [ ] Reverse Engineering
- [ ] Requirements Analysis
- [ ] User Stories
- [ ] Workflow Planning
- [ ] Application Design
- [ ] Units Decomposition

### CONSTRUCTION PHASE
- [ ] Functional Design
- [ ] NFR Requirements
- [ ] Code Generation
- [ ] Build and Test
```

---

## Constraints

- Never overwrite an existing `aidlc-docs/aidlc-state.md` — only resume from it.
- Never create files inside `aidlc-docs/` unless they are documentation.
- Application code always lives at the workspace root.
- Report workspace findings concisely; do not start Requirements Analysis automatically.
