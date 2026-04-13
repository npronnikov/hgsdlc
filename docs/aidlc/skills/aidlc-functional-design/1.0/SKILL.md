---
name: AIDLC Functional Design
description: >
  Perform detailed, technology-agnostic business logic design for a single unit
  of work: domain entities, business rules, validation logic, data flow, and
  (if applicable) frontend component structure. Produces functional design
  artifacts in aidlc-docs/construction/{unit-name}/functional-design/.
---

# AIDLC Functional Design

## Goal
Specify the detailed business logic that will drive code generation — entities,
rules, validations, data transformations — in a technology-agnostic way.
Infrastructure concerns (databases, frameworks, cloud services) belong in
NFR Requirements, not here.

---

## Step 1 — Load Unit Context

Read:
- `aidlc-docs/inception/application-design/unit-of-work.md` — find the target unit
- `aidlc-docs/inception/application-design/unit-of-work-story-map.md` — stories assigned
- `aidlc-docs/inception/application-design/application-design.md` — component interfaces
- `aidlc-docs/inception/requirements/requirements.md` — original requirements

---

## Step 2 — Design Domain Model

For each domain entity in the unit:
- Attributes (name, type, nullability, constraints)
- Relationships (OneToMany, ManyToOne, etc.)
- Invariants (rules that must always hold)
- Value objects (immutable, identity by value)
- Aggregate boundaries (which entity owns which)

---

## Step 3 — Design Business Rules

For each business rule:
- Pre-conditions (what must be true before the operation)
- Post-conditions (what must be true after)
- Validation logic (field-level, cross-field, cross-entity)
- Decision logic (branching conditions)
- Error cases (what to raise / return when rules are violated)

---

## Step 4 — Design Data Flow

For each operation in the unit:
- Input data (source, format, required fields)
- Transformation steps (in order)
- Output data (destination, format)
- Side effects (events, notifications, audit records)

---

## Output Format

Create files in `aidlc-docs/construction/{unit-name}/functional-design/`:

### domain-entities.md
```markdown
# Domain Entities — [unit-name]

## [EntityName]
- **Type**: Aggregate Root / Entity / Value Object
- **Attributes**:
  | Field | Type | Nullable | Constraints |
  |-------|------|----------|-------------|
  | id | UUID | No | PK, generated |
  | name | String | No | max 255 |
- **Relationships**:
  - `items: List<Item>` (OneToMany, cascade ALL)
- **Invariants**: [list of rules that must always be true]

## [EntityName2] ...
```

### business-rules.md
```markdown
# Business Rules — [unit-name]

## BR-1: [Rule title]
**Applies to**: [EntityName#operationName]
**Pre-condition**: [what must be true]
**Logic**: [decision tree or validation steps]
**Error**: [exception / error code / message when violated]

## BR-2: ...
```

### data-flow.md
```markdown
# Data Flow — [unit-name]

## Operation: [operationName]
**Input**: [fields and types]
**Steps**:
1. Validate [field] against BR-1
2. Fetch [EntityName] by [id]
3. Apply [transformation]
4. Persist [result]
5. Emit [EventName] (if applicable)
**Output**: [response structure]
**Error paths**: [what can go wrong and how it surfaces]
```

### frontend-components.md (only if unit has UI)
```markdown
# Frontend Components — [unit-name]

## Component: [ComponentName]
- **Responsibility**: [what it renders / handles]
- **Props**: [list with types]
- **State**: [local state fields]
- **User Interactions**: [events and their handlers]
- **API Calls**: [which endpoints it calls and when]
- **Validation**: [client-side rules]
- **data-testid**: [automation-friendly IDs for interactive elements]

## Component Hierarchy
[ASCII or Mermaid tree of parent → child relationships]
```

---

## Constraints

- Stay technology-agnostic — no Spring, JPA, React-specific annotations here.
- Brownfield: align entities with existing table names from reverse-engineering artifacts.
- Do NOT write implementation code — only models, rules, and data flows.
- Every entity attribute must have a type and nullability specified.
- Every business rule must have a clear error case defined.
