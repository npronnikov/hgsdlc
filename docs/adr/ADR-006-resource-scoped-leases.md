# ADR-006 — Resource-Scoped Leases for Concurrency Control

**Status:** Accepted
**Date:** 2026-03-11

## Context

Multiple runs могут одновременно изменять:

* project repository
* canonical documentation
* artifact families
* target branches

Без coordination возникают:

* merge conflicts
* doc promotion conflicts
* inconsistent state

## Decision

Concurrency контролируется через **resource-scoped leases**.

Supported scopes:

```id="nsmqk9"
project
target_branch
run_branch
canonical_doc
artifact_family
delivery_intent
```

По умолчанию:

* один delivery intent на project + target branch
* doc promotion требует canonical_doc lease

## Consequences

Плюсы:

* предотвращение конфликтов
* predictable concurrency

Минусы:

* дополнительная сложность runtime
