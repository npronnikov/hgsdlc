# ADR-004 — Mandatory Durable Checkpoints Before Wait States

**Status:** Accepted
**Date:** 2026-03-11

## Context

Flow execution может ожидать:

* human approval
* questionnaire completion
* external input

Если execution state не сохранён перед wait state:

* restart приведёт к потере данных
* review previews исчезнут
* evidence может быть потеряно

## Decision

Перед переходом в любой `waiting_gate` runtime обязан создать **durable checkpoint**.

Checkpoint включает:

Evidence CAS:

* artifacts
* logs
* diffs
* run snapshot manifest

Review Projections:

* previews
* reviewer inbox payload
* questionnaire forms

## Consequences

Плюсы:

* restart-safe execution
* стабильный reviewer UX
* полный audit trail

Минусы:

* увеличение storage usage
* дополнительная запись данных
