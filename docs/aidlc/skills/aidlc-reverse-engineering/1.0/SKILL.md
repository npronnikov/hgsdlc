---
name: AIDLC Reverse Engineering
description: >
  Perform a comprehensive analysis of an existing codebase and generate the full
  set of AIDLC reverse-engineering artifacts: business overview, architecture,
  code structure, API documentation, component inventory, technology stack,
  dependency map, and code quality assessment.
---

# AIDLC Reverse Engineering

## Goal
Produce a complete, accurate picture of the existing system so that all subsequent
AIDLC stages (requirements, design, code generation) can make informed decisions
without re-reading the whole codebase each time.

---

## Step 1 — Multi-Package Discovery

Scan the entire workspace root:

1. **Package structure**: identify all packages, modules, services, and their types
   (Application, Infrastructure/CDK/Terraform, Model/Client, Test).
2. **Business context**: understand what the system does at a business level and list
   the business transactions it implements.
3. **Infrastructure**: locate CDK/Terraform/CloudFormation stacks, Docker configs,
   deployment scripts.
4. **Build system**: identify Maven, Gradle, npm, Brazil, or other build tools and
   their inter-package dependencies.
5. **Service architecture**: Lambda handlers, container entrypoints, API definitions
   (Smithy, OpenAPI), data stores.
6. **Code quality signals**: test coverage indicators, linting configs, CI/CD pipelines.

---

## Step 2 — Generate Business Overview

Create `aidlc-docs/inception/reverse-engineering/business-overview.md`:

```markdown
# Business Overview

## Business Context Diagram
[Mermaid C4 or block diagram]

## Business Description
- **What the system does**: [plain-language description]
- **Business Transactions**: [list each transaction with a one-line description]
- **Business Dictionary**: [key terms and their meaning in this system]

## Per-Component Business View
### [Component Name]
- **Purpose**: what it does from the business perspective
- **Responsibilities**: key responsibilities
```

---

## Step 3 — Generate Architecture Documentation

Create `aidlc-docs/inception/reverse-engineering/architecture.md`:

- System overview (high-level description)
- Mermaid architecture diagram (all packages, services, data stores, relationships)
- Per-component descriptions (purpose, responsibilities, dependencies, type)
- Data flow sequence diagrams for key workflows
- Integration points (external APIs, databases, third-party services)
- Infrastructure components (CDK stacks, deployment model, networking)

---

## Step 4 — Generate Code Structure Documentation

Create `aidlc-docs/inception/reverse-engineering/code-structure.md`:

- Build system type and configuration
- Mermaid class diagram or module hierarchy for key classes
- Full existing-files inventory with purpose per file (these are modification candidates)
- Design patterns in use (location, purpose, implementation notes)
- Critical dependencies (version, usage, purpose)

---

## Step 5 — Generate API Documentation

Create `aidlc-docs/inception/reverse-engineering/api-documentation.md`:

- REST endpoints (method, path, purpose, request/response, auth)
- Internal interfaces/classes (methods, parameters, return types)
- Data models (fields, relationships, validation rules)

---

## Step 6 — Generate Component Inventory

Create `aidlc-docs/inception/reverse-engineering/component-inventory.md`:

- Application packages
- Infrastructure packages
- Shared packages (models, utilities, clients)
- Test packages
- Total counts per category

---

## Step 7 — Generate Technology Stack

Create `aidlc-docs/inception/reverse-engineering/technology-stack.md`:

- Programming languages (version, usage)
- Frameworks (version, purpose)
- Infrastructure services
- Build tools
- Testing tools

---

## Step 8 — Generate Dependencies

Create `aidlc-docs/inception/reverse-engineering/dependencies.md`:

- Mermaid internal dependency diagram
- Per-package dependency table (type: compile/runtime/test, reason)
- External dependencies (version, purpose, license)

---

## Step 9 — Generate Code Quality Assessment

Create `aidlc-docs/inception/reverse-engineering/code-quality-assessment.md`:

- Test coverage (overall, unit, integration)
- Code quality indicators (linting, style consistency, documentation)
- Technical debt items (description and location)
- Patterns and anti-patterns found

---

## Step 10 — Update State Tracking

Update `aidlc-docs/aidlc-state.md`:
- Mark Reverse Engineering as `[x]` with timestamp
- Record artifact locations

---

## Constraints

- Analyze the ACTUAL workspace — never fabricate file contents or class names.
- Keep all artifacts in `aidlc-docs/inception/reverse-engineering/`; never write
  application code into `aidlc-docs/`.
- If a component type is absent (e.g., no infrastructure), note "N/A" rather than
  omitting the section.
- Diagrams must use valid Mermaid syntax (test mentally before writing).
