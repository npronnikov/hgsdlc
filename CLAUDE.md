# Human-Guided SDLC

A human-guided software development lifecycle platform.

---

# Project Mission

Build a **human-guided SDLC platform** with:

* **Canonical authored Markdown in Git** — source of truth for workflows
* **Compiled IR as executable semantics** — runtime executes IR, never raw Markdown
* **Control-plane DB as canonical run state** — database is the state authority
* **Evidence CAS for immutable execution evidence** — content-addressed storage
* **Review Projections for reviewer-facing UX** — derived, regenerable views
* **Typed artifact contracts** — strong typing across boundaries
* **Policy-governed execution** — gates and compliance enforced
* **Resource-scoped leases** — controlled resource allocation
* **Signed release provenance** — verifiable artifact origins
* **Budgeted execution** — resource limits and tracking
* **Deterministic resume** — recoverable, resumable workflows

---

# Architecture Rules

1. **Runtime executes compiled IR only**
   Runtime must never derive execution semantics directly from Markdown.

2. **Control-plane DB is canonical for run state**
   Database is the single source of truth for runs, events, approvals and leases.

3. **Evidence CAS is canonical for immutable evidence**
   All execution evidence must be content-addressed and immutable.

4. **Review Projections are derived**
   Reviewer UX data must be regenerable from canonical state.

5. **Mirrors only**
   `state.md`, `audit.md`, and `run-summary.md` are mirrors and must never be canonical.

6. **Separate snapshots**
   Install baseline and immutable run snapshot must stay separate.

7. **Checkpoint before gates**
   Runtime must persist a durable checkpoint before entering any wait state.

8. **Platform gates complement Git governance**
   Platform gates augment but never replace Git PR policies.

---

# Technology Stack

Backend:

* Java 21
* Spring Boot
* Gradle
* PostgreSQL
* Flyway migrations
* Jackson
* JUnit 5
* Testcontainers

Frontend:

* React
* TypeScript
* Vite

Infrastructure:

* Docker
* PostgreSQL

Developer tooling:

* Gradle wrapper
* jdtls (Java language server)
* typescript-language-server

---

# Repository Structure

```
human-guided-sdlc/
├── backend/                     # Control plane backend (Spring Boot modular monolith)
│
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradlew
│   ├── gradle/
│   │   └── wrapper/
│   │
│   └── src/
│       ├── main/java/com/example/sdlc/
│       │
│       │   SdlcApplication.java
│       │
│       │   shared/              # Core primitives and shared utilities
│       │   platform/            # Infrastructure and application configuration
│       │
│       │   authoring/           # Flow and skill authoring logic
│       │   compiler/            # Markdown → IR compiler (T1)
│       │   registry/            # Flow/skill release registry (T2)
│       │   installer/           # Project installer (T4)
│       │   context/             # Context builder (T7a)
│       │   policy/              # Policy decision plane (T8)
│       │   runtime/             # Execution runtime engine (T9)
│       │   evidence/            # Evidence CAS
│       │   projections/         # Review projections
│       │   delivery/            # Git delivery and PR integration
│       │   audit/               # Audit logging
│       │   rbac/                # Role-based access control
│       │   simulation/          # Flow simulation harness (T11)
│       │
│       └── resources/
│           ├── application.yml
│           └── db/migration/
│
├── frontend/                    # Web UI (Runner console, flow designer, catalog)
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   └── src/
│       ├── app/
│       ├── pages/
│       ├── widgets/
│       ├── features/
│       ├── entities/
│       └── shared/
│
├── infra/                       # Local infrastructure setup
│   └── docker/
│       └── compose.yml
│
├── examples/                    # Example flows and skills
│   ├── flows/
│   └── skills/
│
├── scripts/                     # Utility scripts
│   ├── dev/
│   └── ci/
│
└── docs/                        # Architecture and ADRs
```

---

# Backend Commands (Java / Gradle)

Use the **Gradle wrapper only**.

```
# Build backend
cd backend
./gradlew build

# Run tests
./gradlew test

# Run full verification
./gradlew check

# Run the application
./gradlew bootRun

# Clean build
./gradlew clean build
```

Never use system Gradle. Always use `./gradlew`.

---

# Database

PostgreSQL is the canonical data store.

All schema changes must be done via **Flyway migrations**.

Migration files:

```
backend/src/main/resources/db/migration/
```

Example:

```
V001__init_schema.sql
V002__runtime_tables.sql
V003__registry_tables.sql
```

Rules:

* never modify existing migrations
* always add a new migration
* migrations must be idempotent

---

# Frontend Commands

```
# Install dependencies
cd frontend
npm install

# Run dev server
npm run dev

# Run tests
npm test

# Lint
npm run lint

# Typecheck
npm run typecheck

# Production build
npm run build
```

---

# Development Rules

## Phase-first development

Always follow the roadmap phases.

Never implement:

* marketplace
* recommendation engine
* advanced analytics

before **Phase 0 execution kernel is stable**.

---

## Modular Monolith

Backend must remain a **single deployable application**.

Modules must be implemented via **package boundaries**, not microservices.

Avoid premature service decomposition.

---

## Database Safety

* schema changes require migrations
* migrations must be reviewed
* runtime state must always be reconstructable

---

## Execution Safety

Before entering any gate:

* runtime must persist evidence
* projections must be generated
* checkpoint must be durable

---

## Policy Enforcement

All governance decisions must go through **Policy Decision Plane**.

Never embed governance logic directly in runtime services.

---

## Git Governance

Platform delivery must integrate with Git:

* feature branches per run
* pull requests
* branch protections
* CI checks

Platform gates must **not replace Git review processes**.

---

# Coding Conventions

Java:

* Java 21
* Prefer immutable objects
* Use records where appropriate
* Avoid reflection-heavy frameworks

Spring:

* use constructor injection
* avoid field injection
* prefer explicit configuration

Testing:

* JUnit 5
* Testcontainers for PostgreSQL
* integration tests for runtime engine

---

# Development Workflow

1. Read architecture docs and ADRs
2. Implement one roadmap task at a time
3. Keep PRs small and focused
4. Add tests for every module
5. Run:

```
./gradlew check
```

before committing.

---

# Claude Code Guidelines

When generating code:

* respect architecture rules
* keep modules isolated
* avoid introducing new frameworks
* do not implement future phases prematurely
* do not modify docs automatically

Prefer incremental implementation aligned with:

```
docs/roadmap-phase0.md
```

Tasks must follow the execution kernel order:

```
T1 → T2 → T4 → T7a → T8 → T9 → T10 → T11 → T12 → T13 → T14 → T15
```
