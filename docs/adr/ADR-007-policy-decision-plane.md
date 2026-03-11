# ADR-007 — Policy Decision Plane for Governance

**Status:** Accepted
**Date:** 2026-03-11

## Context

Platform governance включает:

* run start permissions
* approval authority
* tool capabilities
* network access
* doc promotion
* delivery actions
* budget escalation

Если эти решения принимаются внутри runtime logic, система становится:

* неаудируемой
* трудно изменяемой

## Decision

Все governance decisions принимаются через **Policy Decision Plane (PDP)**.

PDP принимает решения по:

```id="2w2p7p"
subject
action
resource
context
```

И возвращает:

```id="ixu6r0"
decision
reason_codes
policy_set_hash
```

Каждый run фиксирует `policy_snapshot_id`.

## Consequences

Плюсы:

* explainable governance
* auditability
* policy evolution

Минусы:

* дополнительный сервис
