# HGSDLC Implementation Roadmap

This roadmap targets the requirements in `docs/spec/full-requirements-v5.md` and organizes delivery into phased, testable milestones.

## 0. Assumptions and Scope

1. v1 scope is aligned with v5: YAML flow, Markdown rule/skills, Qwen CLI adapter, simplified project context, single Spring Boot monolith, PostgreSQL as execution truth.
2. Non-goals for v1: compiled IR, signed provenance, CAS/projections, PDP/leases/budgets (unless explicitly added later).
3. One active run per `project + target_branch` is enforced.
4. Enforcement allowlist is platform-level; `RULE.md.allowed_commands` is advisory.

## 1. Roadmap Structure

1. Phase A: Project foundation and schema validation
2. Phase B: Versioning, publish, and registry persistence
3. Phase C: Runtime kernel and state machine
4. Phase D: Agent integration and prompt package
5. Phase E: Human gates and UI console
6. Phase F: External command nodes and delivery
7. Phase G: Hardening, audit, idempotency, and regression suite

## 2. Phase A — Foundation and Validation

### Goals

1. Working backend skeleton with health endpoint and test baseline.
2. JSON/YAML parsing and validation pipelines for flow, rule, and skill.
3. Initial DB schema and Liquibase migrations.

### Deliverables

1. Flow parser for `flow.yaml` and validators for `flow.schema.json` and node schemas.
2. Rule/skill Markdown parser with frontmatter extraction and schema validation.
3. Error model for validation failures returned via API.
4. Initial persistence for flows/rules/skills with draft status.

### Acceptance Criteria

1. API can save draft flow/rule/skill and returns validation results.
2. Invalid documents are rejected with clear validation errors.
3. Unit tests cover sample fixtures (`simple-flow.md`, `complex-flow.md`, `simple-skill.md`).

## 3. Phase B — Versioning and Publish Workflow

### Goals

1. Implement `publish=true` flow for flow/rule/skill with patch version auto-increment.
2. Enforce `canonical_name = {id}@{semver}`.
3. Ensure published flow references only published rule/skills.

### Deliverables

1. Save endpoints that support `resource_version` and optimistic locking.
2. Published version storage with immutable snapshot behavior.
3. Resolver for latest draft vs published view per entity.

### Acceptance Criteria

1. `POST /api/flows/{id}/save` with `publish=true` increments version and persists immutable published record.
2. Flow publish fails if referenced rule/skills are not published.
3. Concurrent saves are rejected by `resource_version`.

## 4. Phase C — Runtime Kernel and State Machine

### Goals

1. Implement run creation and state transitions in DB.
2. Deterministic project context builder.
3. Run snapshot creation.

### Deliverables

1. `POST /api/runs` validates `flow_canonical_name`, resolves dependencies, enforces concurrency guard.
2. Run snapshot stores raw flow/rule/skills plus checksums and context manifest.
3. Node execution records with `attempt_no` and terminal states.

### Acceptance Criteria

1. A run moves through `created -> running -> waiting_gate -> running -> completed` for a simple flow.
2. `context_root_dir` produces deterministic manifest and checksum.
3. Restart semantics are documented and enforced via policy-driven retry.

## 5. Phase D — Agent Integration and Prompt Package

### Goals

1. Qwen CLI adapter and prompt builder.
2. Qwen materialization: `.qwen/QWEN.md` and `.qwen/skills/*/SKILL.md`.
3. Prompt package stored in DB and displayed in UI.

### Deliverables

1. Prompt package builder with required sections in the spec.
2. Agent response validation via `agent-response.schema.json`.
3. Output artifact versioning in DB; `content_text` stored as raw bytes.

### Acceptance Criteria

1. AI node runs Qwen in headless mode and saves stdout/stderr/response.
2. Invalid agent responses fail node with clear error.
3. Prompt checksum is saved in audit and queryable.

## 6. Phase E — Human Gates and Console UI

### Goals

1. Human input and human approval gates.
2. Run console UI with timeline, artifacts, prompt view, and audits.

### Deliverables

1. Gate lifecycle stored in DB with optimistic locking.
2. UI pages: run list, run console, gate input, gate approval.
3. Artifact review shows exact artifact versions.

### Acceptance Criteria

1. `human_input` gate enforces validation rules and creates new artifact version on submit.
2. `human_approval` gate supports approve/reject/rework routes.
3. UI can resume open gates after backend restart.

## 7. Phase F — External Commands and Delivery

### Goals

1. External command execution with allowlist enforcement.
2. Commit result node (no push/PR in v1).

### Deliverables

1. Command validator with platform allowlist.
2. External command node execution with stdout/stderr capture.
3. Git commit action and result artifact.

### Acceptance Criteria

1. Non-allowlisted command is rejected before execution.
2. Commit node creates commit on run branch and saves `git-commit-result` artifact.
3. Rework rollback node resets workspace when configured.

## 8. Phase G — Hardening, Audit, and Regression Suite

### Goals

1. Full audit views and delta summary.
2. Idempotency and optimistic locking on mutations.
3. Regression test suite for core flows.

### Deliverables

1. Audit endpoints: runtime, agent, review.
2. Summary delta computation on each executor node.
3. Test flows covering rework, retry, and rollback.

### Acceptance Criteria

1. Audit view includes prompt checksum, skills declared/injected/used.
2. Mutations require `Idempotency-Key` and `resource_version`.
3. Regression flows run end-to-end locally via scripts.

## 9. Workstreams and Ownership

1. Backend core: flow/rule/skill parsing, persistence, validation, run state machine.
2. Runtime engine: node execution, gates, artifacts, audits.
3. Agent integration: Qwen CLI adapter, prompt package, response validation.
4. UI: editors, run console, gate screens.
5. Infra: DB migrations, local dev compose, CI scripts.
6. QA: fixtures, integration tests, end-to-end flows.

## 10. Risks and Mitigations

1. Qwen CLI instability: mitigate with robust process timeouts and stdout/stderr capture.
2. Artifact size growth: monitor DB size and plan for external blob store if needed.
3. Concurrency conflicts: enforce strong locking on run and gate versions.
4. Validation drift: keep schemas versioned and test fixtures in CI.

## 11. Suggested Milestone Order

1. A1: Parsers and schemas for flow/rule/skill.
2. A2: Draft save + validation APIs.
3. B1: Publish workflow and canonical naming.
4. C1: Run creation + deterministic context.
5. C2: State machine and node execution records.
6. D1: Prompt package + Qwen adapter.
7. E1: Human input gate + UI.
8. E2: Human approval gate + UI.
9. F1: External command nodes + commit.
10. G1: Audit, delta summary, idempotency hardening.

## 12. Definition of Done (v1)

1. Published flow executes end-to-end with Qwen agent and human gates.
2. All artifacts are versioned and stored; prompt package is auditable.
3. Run can resume after restart with policy-driven retry.
4. External command nodes are allowlist enforced.
5. UI supports authoring, run execution, and gate decisions.
