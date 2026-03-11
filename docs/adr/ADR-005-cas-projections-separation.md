# ADR-005 — Evidence CAS and Review Projections Separation

**Status:** Accepted
**Date:** 2026-03-11

## Context

Execution produces two categories of data:

1. immutable execution evidence
2. reviewer-facing representations

Смешивание этих типов данных приводит к:

* невозможности redaction
* проблемам с retention policies
* тяжелым UI payloads

## Decision

Evidence storage разделяется на два слоя.

### Evidence CAS

Stores:

* raw artifacts
* logs
* diffs
* snapshots

Properties:

* immutable
* content-addressed

---

### Review Projections

Stores:

* previews
* rendered markdown
* questionnaire payloads
* reviewer summaries

Properties:

* derived
* regenerable

## Consequences

Плюсы:

* оптимизированный reviewer UX
* безопасная redaction
* независимые retention policies

Минусы:

* необходимость projection regeneration
