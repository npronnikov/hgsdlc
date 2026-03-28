# ADR: Runtime CQRS Finalization (Stage H)

- Date: 2026-03-28
- Status: Accepted

## Context

Runtime refactoring moved orchestration from `RuntimeService` into dedicated command/query services and domain services.  
The remaining risks were:

1. `RuntimeService` still acting as a historical god-class surface.
2. Nested `record` types in `RuntimeService` coupling the whole runtime module to one class.
3. No automated architectural guardrails to prevent reverse drift.

## Decision

1. Keep `RuntimeService` as a temporary **thin facade** only.
2. Move all public nested command/result records from `RuntimeService` into dedicated types:
   - `ru.hgd.sdlc.runtime.application.command.*`
   - `ru.hgd.sdlc.runtime.application.dto.*`
3. Switch API and application services to the new command/DTO types directly.
4. Add ArchUnit tests that enforce:
   - runtime API depends on `RuntimeCommandService` / `RuntimeQueryService` only from runtime service layer;
   - query-side services do not depend on command package;
   - command-side services do not depend on query-side services;
   - `runtime.application.service` does not use direct CLI/FS primitives (`ProcessBuilder`, `Files`, `RandomAccessFile`);
   - write-side services depend on `RuntimeStepTxService`.

## Consequences

- `RuntimeService` is no longer the center of runtime logic and can be removed later with low risk.
- Runtime command/query contracts are explicit and independently reusable.
- Architectural constraints are now executable tests and will fail CI on regressions.

