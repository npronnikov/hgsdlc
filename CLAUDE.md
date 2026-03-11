# Human-Guided SDLC

A human-guided software development lifecycle platform.

## Project Mission

Build a human-guided SDLC platform with:

- **Canonical authored Markdown in Git** - source of truth for workflows
- **Compiled IR as executable semantics** - runtime executes IR, never raw Markdown
- **Control-plane DB as canonical run state** - database is the state authority
- **Evidence CAS for immutable execution evidence** - content-addressed storage
- **Review Projections for reviewer-facing UX** - derived, regenerable views
- **Typed artifact contracts** - strong typing across boundaries
- **Policy-governed execution** - gates and compliance enforced
- **Resource-scoped leases** - controlled resource allocation
- **Signed release provenance** - verifiable artifact origins
- **Budgeted execution** - resource limits and tracking
- **Deterministic resume** - recoverable, resumable workflows

## Architecture Rules

1. **Runtime executes compiled IR only** - never re-derive execution semantics from raw Markdown at run time
2. **Control-plane DB is canonical for run state** - database is the single source of truth
3. **Evidence CAS is canonical for immutable evidence** - evidence is content-addressed and immutable
4. **Review Projections are derived and regenerable** - not stored as canonical state
5. **Mirrors only** - `state.md`, `audit.md`, `run-summary.md` are mirrors, never canonical
6. **Separate snapshots** - install baseline and immutable run snapshot must stay separate
7. **Checkpoint before gates** - checkpoint is mandatory before any waiting gate
8. **Platform gates complement Git PR governance** - they do not replace it

## Repository Structure

```
human-guided-sdlc/
├── backend/           # Control plane, compiler, runtime, registry, policy, simulation
│   ├── src/app/
│   │   ├── api/       # API endpoints
│   │   ├── domain/    # Domain models
│   │   ├── services/  # Business logic
│   │   ├── infra/     # Infrastructure
│   │   ├── schemas/   # Data schemas
│   │   ├── db/        # Database layer
│   │   ├── runtime/   # Execution runtime
│   │   ├── policy/    # Policy engine
│   │   ├── registry/  # Artifact registry
│   │   ├── compiler/  # Markdown → IR compiler
│   │   ├── simulation/# Dry-run simulation
│   │   ├── audit/     # Audit logging
│   │   ├── leases/    # Resource leases
│   │   └── budgets/   # Execution budgets
│   └── tests/         # Test suite
├── frontend/          # Runner console, flow designer, skill catalog
│   └── src/
│       ├── app/       # Application core
│       ├── pages/     # Page components
│       ├── widgets/   # UI widgets
│       ├── entities/  # Domain entities
│       └── shared/    # Shared utilities
├── docker/            # Docker configuration
├── docs/              # ADRs and design docs
│   ├── architecture/  # Architecture docs
│   └── adr/           # Architecture Decision Records
├── examples/          # Example code and usage
└── scripts/           # Utility scripts
```

## Backend Commands (Python)

```bash
# Create virtual environment
cd backend && python -m venv .venv

# Activate environment
source backend/.venv/bin/activate

# Install dependencies
cd backend && pip install -e ".[dev]"

# Run tests
cd backend && pytest

# Lint
cd backend && ruff check .

# Format
cd backend && ruff format .

# Type check
cd backend && pyright
```

## Frontend Commands

```bash
# Install dependencies
cd frontend && npm install

# Development server
cd frontend && npm run dev

# Run tests
cd frontend && npm test

# Lint
cd frontend && npm run lint

# Type check
cd frontend && npm run typecheck

# Production build
cd frontend && npm run build
```

## Development Rules

- **Work phase-by-phase** - prove execution kernel before rich UX
- **Prefer modular monolith** - avoid premature service split
- **DB schema changes require migrations** - no exceptions
- **Gate wait states require checkpoints** - durable checkpoint evidence mandatory
- **Stabilize core first** - never implement marketplace/recommendations before T1/T2/T4/T8/T9/T10/T11/T12 are stable
- **Keep PRs narrow** - aligned to one task from the roadmap
