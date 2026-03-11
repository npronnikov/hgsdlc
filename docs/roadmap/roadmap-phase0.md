# Phase 0 Roadmap — Execution Kernel

## Цель фазы

Доказать, что платформа жизнеспособна как **governed SDLC execution kernel**, где:

- Markdown является каноном authoring
- executable semantics определяются **compiled IR**
- runtime исполняет **stateful execution**
- контроль осуществляется через **policy decisions**
- execution является **auditable, resumable и deterministic**

Phase 0 **не включает**:
- marketplace
- recommendations
- сложный authoring UI
- advanced context intelligence

Фокус: **execution kernel + governance + delivery**

---

# Общая последовательность

Execution kernel строится в следующем порядке:

1. Canonical Markdown Core + IR Compiler
2. Release pipeline + signed provenance
3. Project installer
4. Context Lite
5. Policy Decision Plane
6. Runtime Engine
7. Agent Adapter
8. Simulation Harness
9. Budget Control
10. Runner Console
11. Git Delivery
12. RBAC + Audit + Observability

---

# T1 — Canonical Markdown Core + IR Compiler

## Цель

Создать canonical Markdown format и compiler pipeline, который превращает flow package в **deterministic executable IR**.

Runtime **никогда не интерпретирует Markdown напрямую**.

---

## Подзадачи

### T1.1 Markdown data models

Создать Pydantic модели для:

- Flow
- Phase
- Node
- ArtifactTemplate
- Skill

Минимальные поля:

Flow:
- id
- version
- phase_order
- start_roles
- resume_policy

Node:
- id
- type
- executor_kind / gate_kind
- inputs
- outputs
- transitions

Artifact:
- id
- logical_role
- schema_id
- path_pattern
- promotion_eligibility

---

### T1.2 Markdown parser

Реализовать parser:

```

Markdown file
→ frontmatter
→ body
→ typed model

```

Требования:

- body сохраняется как opaque текст
- структура живёт в frontmatter
- parser не меняет body

---

### T1.3 Writer (round-trip safe)

Writer должен поддерживать:

- preserve formatting mode
- canonical formatting mode

Round-trip тест:

```

parse → write → parse

```

Body должен оставаться **byte-identical**.

---

### T1.4 Flow validator

Validator проверяет:

- все node ids существуют
- inputs / outputs имеют artifact templates
- transitions валидны
- executor/gate тип корректен
- нет циклов
- backward edges только для rework

---

### T1.5 IR schema

Создать JSON schema:

```

flow.ir.json

```

IR должен содержать:

- flow id
- version
- package checksum
- normalized node graph
- artifact contracts
- transitions
- resolved skills

---

### T1.6 IR compiler

Compiler:

```

Markdown package
→ parsed models
→ validation
→ normalized IR

```

---

### T1.7 CLI

CLI команды:

```

sdlc compile-flow
sdlc validate-flow
sdlc inspect-flow

```

---

### Definition of Done

- parser поддерживает все основные md типы
- round-trip работает
- compiler генерирует deterministic IR
- validator ловит неконсистентные flows
- один и тот же commit → идентичный IR

---

# T2 — Registry + Release Pipeline + Provenance

## Цель

Публиковать flow и skill как **immutable packages**.

Runtime должен использовать **release package**, а не raw repo.

---

## Подзадачи

### T2.1 Release builder

Release pipeline:

```

tagged commit
→ compile IR
→ build package

```

---

### T2.2 Package structure

Release package содержит:

```

flow.md
phases/
nodes/
artifact templates
flow.ir.json
release-manifest.json
provenance.json
checksums

```

---

### T2.3 Registry service

Registry хранит:

- flows
- versions
- skills
- dependencies
- compatibility metadata

---

### T2.4 Lock files

Installer должен генерировать:

```

flow.lock.md
skill-lock.md

```

Lock содержит:

- versions
- checksums
- IR checksum
- provenance refs

---

### T2.5 Provenance

Каждый release должен содержать:

- source repo
- source commit
- build identity
- IR checksum
- signature

---

### Definition of Done

- release собирается из tagged commit
- packages immutable
- provenance проверяется
- installer использует только release package

---

# T4 — Project Bootstrap + Installer

## Цель

Устанавливать flow в проект и создавать **install baseline**.

---

## Подзадачи

### T4.1 Project creation

Поддержать:

```

create project
import existing repo

```

---

### T4.2 Install baseline

Installer создаёт:

```

.sdlc/install/current/

```

Содержимое:

- flow package
- skill snapshots
- flow.lock.md
- skill-lock.md
- install-manifest.md

---

### T4.3 Provenance verification

Installer обязан:

- проверять signatures
- проверять checksums

---

### Definition of Done

- flow можно install
- lock файлы генерируются
- install baseline воспроизводим

---

# T7a — Context Lite

## Цель

Собрать bounded project context для brownfield execution.

---

## Подзадачи

### Repo scanner

Определяет:

- языки
- package managers
- migrations
- tests
- modules

---

### Context files

Создаются:

```

repo-map.md
project-rules.md
constraints.md
architecture-baseline.md

```

---

### Definition of Done

- scan ограничен budget
- context сохраняется в `.sdlc/context`

---

# T8 — Policy Decision Plane

## Цель

Все platform decisions должны приниматься через **policy engine**.

---

## Подзадачи

### Policy API

```

POST /policy/decide

```

---

### Decision log

Каждое решение хранит:

- decision_id
- subject
- action
- resource
- policy_set_hash
- result

---

### Definition of Done

- runtime использует PDP
- policy decisions логируются

---

# T9 — Runtime Engine

## Цель

Исполнять flow по **compiled IR**.

---

## Подзадачи

### Run orchestrator

Run lifecycle:

```

created
running
waiting_gate
completed
failed

```

---

### Node execution

Поддержать:

```

executor
gate

```

---

### Checkpointing

Перед wait state:

```

CAS evidence
review projections

```

---

### Resume

Runtime должен восстановиться из:

```

DB state
CAS evidence
projections

```

---

### Definition of Done

- runtime исполняет IR
- resume работает
- evidence сохраняется

---

# T10 — Qwen Agent Adapter

## Цель

Интегрировать coding agent.

---

## Подзадачи

### Prompt package

Собрать:

- node instructions
- skills
- context
- artifact contracts

---

### Output validation

Adapter проверяет:

- обязательные artifacts
- diff
- exit status

---

### Definition of Done

- executor node работает
- output contract валидируется

---

# T11 — Flow Simulation Harness

## Цель

Прогонять flow без реального агента.

---

## Подзадачи

Simulation поддерживает:

- stub executors
- fake approvals
- restart simulation
- lease conflicts

---

### Definition of Done

- flow можно симулировать
- broken transitions обнаруживаются

---

# T12 — Cost and Budget Control

## Цель

Контролировать execution budgets.

---

## Подзадачи

Budget types:

- runtime duration
- model cost
- changed files
- evidence size

---

### Definition of Done

- budget overruns фиксируются
- runtime реагирует по policy

---

# T13 — Runner Console

## Цель

UI для запуска и мониторинга run.

---

### Возможности

- запуск flow
- stage tracker
- HITL inbox
- previews
- audit panel

---

### Definition of Done

- run можно полностью пройти через UI

---

# T14 — Git Delivery

## Цель

Сохранять результат run в Git.

---

### Branch strategy

```

feature/sdlc/{run_id}

```

---

### Commit modes

- phase commits
- single delivery commit

---

### Definition of Done

- PR создаётся автоматически
- runtime artifacts не коммитятся

---

# T15 — RBAC + Audit + Observability

## Цель

Добавить governance.

---

### RBAC

Роли:

- product_owner
- analyst
- architect
- developer
- qa
- devops
- flow_admin
- skill_admin

---

### Metrics

Минимум:

- runs_started
- runs_completed
- node_duration
- gate_wait_time
- budget_overruns

---

### Definition of Done

- все действия аудируются
- метрики доступны

---

# Phase 0 Exit Criteria

Phase 0 считается успешной, если:

- 2–3 enterprise flows проходят полный цикл

```

install
→ run
→ gate
→ rework
→ delivery
→ PR

```

И выполняются условия:

- execution deterministic
- resume работает
- evidence сохраняется
- policy decisions фиксируются
- budgets enforce-ятся
- provenance проверяется
```

---

## Что сделать сразу после создания этого файла

1️⃣ Положи его в

```
docs/roadmap-phase0.md
```

2️⃣ Сделай commit.

3️⃣ Открой Claude Code и попроси:

```text
/plan
Проанализируй docs/roadmap-phase0.md и предложи implementation plan для T1.
Нужно:
- package structure
- Python modules
- Pydantic models
- parser design
- IR schema
- тесты
```

---

Если хочешь, дальше я могу сделать **ещё 3 очень полезных файла**, которые сильно ускорят разработку:

1️⃣ `docs/architecture/architecture-overview.md`
2️⃣ `docs/adr/adr-001-execution-kernel.md`
3️⃣ **T1 implementation backlog (20 задач)** — идеально для Claude Code.
# Architecture Overview — Human-Guided SDLC Platform

## Purpose

The platform provides a **governed Software Development Lifecycle (SDLC)** execution system where:

* development flows and skills are authored as **canonical Markdown packages**
* packages are compiled into **deterministic executable IR**
* execution is managed by a **stateful control plane**
* all actions are **auditable, resumable and policy-governed**

The system enables **human-guided AI-assisted development** with strict governance and reproducibility.

---

# Architectural Principles

## 1. Git is the source of authored truth

Flow and skill content are authored in Git repositories.

Git stores:

* flow definitions
* skill definitions
* changelogs
* evaluation artifacts
* released source revisions

Git is **not the runtime execution state**.

---

## 2. Compiled IR defines executable semantics

Markdown is an **authoring format only**.

Before execution:

```
Markdown packages
 → validated
 → compiled
 → normalized
 → emitted as IR bundle
```

Runtime executes **compiled IR only**.

Historical runs must never depend on re-parsing Markdown with a different parser version.

---

## 3. Control Plane DB is canonical execution state

All runtime state lives in the control plane database.

Canonical state includes:

* runs
* node states
* events
* approvals
* policy decisions
* lease states
* budget usage
* execution status

The DB must support **deterministic resume**.

---

## 4. Evidence is immutable and content-addressed

All durable execution outputs are stored in **Evidence CAS**.

CAS stores:

* logs
* diffs
* snapshots
* raw artifacts
* execution manifests
* validation evidence

Objects are immutable and referenced by **content hash**.

---

## 5. Review views are projections

Review-friendly views are stored separately as **Review Projections**.

Examples:

* rendered markdown previews
* reviewer inbox payloads
* questionnaire forms
* redacted views
* compare views

Projections are **derived** from canonical facts.

---

## 6. Install baseline is separate from run snapshot

A project stores the **active flow installation**.

```
.sdlc/install/current/
```

A run always references:

* install baseline
* compiled IR bundle
* immutable run snapshot

Historical runs must remain reproducible even if the install baseline changes.

---

## 7. Execution is checkpoint-driven

Any step that waits for human input must first create a **durable checkpoint**.

Checkpoint includes:

* execution outputs
* logs
* patch/diff
* projection previews
* run snapshot manifest

Without checkpointing, wait states are forbidden.

---

## 8. Platform governance complements Git governance

Platform gates handle:

* structured approvals
* questionnaires
* artifact review
* doc promotion

Git governance remains the final layer:

* PR review
* CODEOWNERS
* CI checks
* branch protections

---

# Core System Components

The system consists of five major layers.

---

# 1. Authoring Layer

Responsible for **creating flow and skill packages**.

Main elements:

* Flow repositories
* Skill repositories
* Markdown structure
* Artifact templates
* Evaluation datasets

Markdown packages contain:

```
flow.md
phase.md
node.md
artifact-template.md
skill.md
```

Authoring is **Git-native**.

---

# 2. Release and Package Layer

Transforms authoring content into immutable runtime packages.

Main components:

* Release Builder
* IR Compiler
* Package Resolver
* Registry
* Provenance Signer

Release pipeline:

```
Git commit
 → validation
 → IR compilation
 → package build
 → provenance signing
 → registry publish
```

Packages are **immutable**.

---

# 3. Control Plane

The control plane manages execution lifecycle.

Main services:

* Control Plane API
* Runtime Engine
* Policy Decision Plane
* Lease Manager
* Budget Control
* Simulation Harness

The control plane coordinates:

* run lifecycle
* policy decisions
* resource locks
* execution orchestration

---

# 4. Evidence Layer

Handles durable execution data.

Two subsystems exist.

### Evidence CAS

Stores immutable evidence:

* raw artifacts
* logs
* diffs
* snapshots

### Review Projections

Stores derived views for reviewers:

* previews
* inbox payloads
* summaries
* comparison views

CAS is canonical.
Projections are derived.

---

# 5. Execution Layer

Responsible for performing work inside a sandboxed workspace.

Components:

* Agent Adapter
* Sandbox Manager
* Workspace Manager

The adapter translates executor nodes into agent actions.

Initial agent:

```
qwen-coder-cli
```

Future adapters may support additional agents.

---

# Runtime Execution Model

A flow execution is called a **run**.

Run lifecycle:

```
run created
 → IR resolved
 → policy snapshot created
 → leases acquired
 → workspace created
 → project repo cloned
 → nodes executed
 → checkpoints written
 → gates handled
 → delivery committed
```

The runtime always follows the **compiled IR execution graph**.

---

# Node Model

Two node types exist.

### Executor nodes

Perform automated actions.

Examples:

* AI code generation
* script execution
* scanning
* validation

Executor nodes produce artifacts.

---

### Gate nodes

Pause execution for human input.

Types:

* approval
* questionnaire

Gates support:

* approve
* reject
* request rework

---

# Artifact Model

Outputs are defined through **typed artifact contracts**.

Each artifact has:

* logical role
* schema
* required sections
* promotion eligibility
* retention class
* sensitivity class

Artifacts are validated before node success.

---

# Policy Decision Plane

All governance decisions pass through the PDP.

Examples:

* run start permission
* gate approval authority
* tool capability permissions
* network access
* doc promotion eligibility
* lease overrides
* budget escalation

Every decision must produce a **decision trace**.

---

# Resource-Scoped Leases

Concurrency is controlled using leases.

Lease scopes include:

* project
* target branch
* run branch
* canonical document
* artifact family
* delivery intent

Leases prevent conflicting operations.

---

# Budget Governance

Execution is limited by **budget envelopes**.

Budgets include:

* runtime duration
* model cost
* changed files
* evidence size
* patch size
* tool invocations

Budget overruns trigger policy actions.

---

# Simulation Harness

Flows must support simulation before production execution.

Simulation features:

* deterministic executor stubs
* fake gate decisions
* transition validation
* restart scenarios
* lease conflict simulation
* budget violation tests

Simulation evidence becomes part of the release metadata.

---

# Phase 0 Scope

Phase 0 proves the viability of the execution kernel.

Included components:

* Markdown Core + IR Compiler
* Registry + Release Pipeline
* Project Installer
* Context Lite
* Policy Decision Plane
* Runtime Engine
* Agent Adapter
* Simulation Harness
* Budget Control
* Runner Console
* Git Delivery
* RBAC + Audit

Excluded from Phase 0:

* marketplace
* recommendations
* advanced catalog
* semantic code intelligence
* automatic improvement engine

---

# Key Architectural Risks

### Markdown round-trip corruption

Mitigation:

* structure/body split
* UI-managed regions
* byte-preservation tests

---

### IR drift from source

Mitigation:

* deterministic compiler
* IR checksums
* release provenance

---

### Policy bypass

Mitigation:

* centralized policy plane
* adapter enforcement
* sandbox restrictions

---

### Evidence loss

Mitigation:

* mandatory checkpointing
* CAS immutability

---

### Concurrency conflicts

Mitigation:

* resource-scoped leases

---

# Phase 0 Exit Criteria

The execution kernel is considered validated when:

* multiple enterprise flows complete the lifecycle

```
install
 → run
 → gate
 → rework
 → delivery
 → PR
```

with:

* deterministic execution
* successful resume after restart
* policy-governed actions
* evidence preservation
* budget enforcement
* provenance verification
