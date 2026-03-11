# ADR-008 — Budgeted Execution Model

**Status:** Accepted
**Date:** 2026-03-11

## Context

AI-assisted execution может привести к:

* runaway model costs
* huge patches
* excessive artifacts
* long execution times

Без ограничений execution становится неконтролируемым.

## Decision

Каждый run исполняется в **budget envelope**.

Budget types:

* max_duration_seconds
* max_attempts
* max_model_cost
* max_patch_bytes
* max_changed_files
* max_evidence_bytes

Budget exceed triggers **policy-governed action**.

## Consequences

Плюсы:

* cost control
* operational safety
* predictable execution

Минусы:

* иногда требуется manual escalation
