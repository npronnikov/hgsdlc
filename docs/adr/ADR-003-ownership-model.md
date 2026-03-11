# ADR-003 — Canonical Ownership Model

**Status:** Accepted
**Date:** 2026-03-11

## Context

Система хранит данные в нескольких местах:

* Git
* Control Plane DB
* Evidence storage
* Project repository
* Review UI

Без чёткого ownership model возникает путаница:

* где хранится каноническая правда
* какие данные являются derived

## Decision

Каждый тип данных имеет **единственный canonical source**.

### Authored Content

Canonical source:

Git repositories

Содержит:

* flows
* skills
* evaluation artifacts

---

### Execution Semantics

Canonical source:

Release package + compiled IR

---

### Execution State

Canonical source:

Control Plane DB

Содержит:

* runs
* node states
* events
* approvals
* policy decisions

---

### Evidence

Canonical source:

Evidence CAS

Содержит:

* logs
* diffs
* artifacts
* snapshots

---

### Review UX

Canonical source:

Review Projections

Derived from DB + CAS.

---

### Mirrors

`state.md`, `audit.md`, `run-summary.md` являются **derived mirrors**.

## Consequences

Плюсы:

* ясная ownership модель
* отсутствие конфликтов данных
* deterministic resume

Минусы:

* необходимость синхронизации derived mirrors
