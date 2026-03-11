# ADR-002 — Compiled IR as Canonical Execution Semantics

**Status:** Accepted
**Date:** 2026-03-11

## Context

Flow authoring выполняется в Markdown.

Однако Markdown:

* плохо подходит для deterministic execution
* зависит от parser version
* не нормализован
* может измениться после release

Для воспроизводимого исполнения необходима стабильная intermediate representation.

## Decision

Runtime будет исполнять **compiled IR**, а не Markdown.

Release pipeline выполняет:

```id="6n0ntn"
Markdown package
 → validation
 → normalization
 → compilation
 → IR bundle
```

IR становится canonical execution semantics для конкретного release.

IR bundle включает:

* execution graph
* artifact contracts
* resolved transitions
* skill checksums
* policy hooks
* schema version

## Consequences

Плюсы:

* deterministic execution
* reproducible historical runs
* runtime не зависит от parser version
* execution semantics фиксируется в release

Минусы:

* усложнение release pipeline
* необходимость поддержки IR schema evolution

## Alternatives Considered

### Interpret Markdown at runtime

Rejected.

Это приводит к:

* drift semantics
* parser incompatibility
* non-deterministic execution
