# ADR-001 — Execution Kernel First

**Status:** Accepted
**Date:** 2026-03-11

## Context

Платформа включает множество подсистем:

* authoring UX
* marketplace
* recommendation engine
* semantic code intelligence
* flow execution runtime
* policy enforcement
* artifact governance

Риск состоит в том, что развитие UI и catalog features может начаться раньше, чем будет доказана жизнеспособность execution модели.

## Decision

Разработка будет следовать принципу **Execution Kernel First**.

Первая фаза разработки (Phase 0) включает только:

* Markdown authoring model
* IR compiler
* release pipeline
* control plane runtime
* policy decision plane
* execution adapter
* simulation harness
* budget governance
* runner console
* git delivery
* audit and RBAC

Следующие возможности **не входят** в Phase 0:

* marketplace
* recommendations
* advanced catalog
* automatic improvement engine
* deep semantic code intelligence

## Consequences

Плюсы:

* минимизация архитектурного риска
* быстрый feedback loop
* проверка ключевых инвариантов системы

Минусы:

* UI будет ограниченным
* early adopters не увидят marketplace features

## Alternatives Considered

### Build UI-first platform

Rejected.

UI без стабильного execution kernel приведёт к архитектурной нестабильности.

### Marketplace-first

Rejected.

Marketplace имеет смысл только после доказанной execution модели.
