# ADR-009 — Simulation Before Production Execution

**Status:** Accepted
**Date:** 2026-03-11

## Context

Flow definitions могут содержать:

* broken transitions
* missing artifact contracts
* invalid rework loops

Production runs не должны быть первым местом обнаружения таких ошибок.

## Decision

Каждый flow должен поддерживать **simulation mode**.

Simulation harness позволяет:

* stub executors
* fake approvals
* contract validation
* restart simulation
* lease conflict testing

Simulation evidence хранится вместе с release metadata.

## Consequences

Плюсы:

* safer releases
* faster debugging
* better coverage

Минусы:

* дополнительная инфраструктура
