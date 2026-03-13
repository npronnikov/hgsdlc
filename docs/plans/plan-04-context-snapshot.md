# T4 Implementation Plan — Context and Snapshot

## Overview

**Цель:** Реализовать детерминированный project context и run snapshot, фиксирующий входные артефакты execution.

**Ключевой инвариант:** одинаковый `context_root_dir` дает идентичный manifest checksum.

---

## 1. Scope T4 для Phase 0

### Входит в scope

| Компонент | Описание |
|-----------|----------|
| Context builder | Recursive scan и checksum | 
| Context manifest | Stable lexical order |
| Run snapshot | Raw + parsed models + checksums |

### НЕ входит в scope (Phase 0)

| Компонент | Причина |
|-----------|---------|
| Semantic context | Не требуется в v1 |
| Auto context merge | Только deterministic scan |

---

## 2. Conceptual Architecture

```mermaid
flowchart LR
  A[Repo root] --> B[Context scanner]
  B --> C[Manifest JSON]
  C --> D[Checksum]
  D --> E[Run snapshot]
```

---

## 3. Implementation Slices

### Slice 1: Context Root Validation (2h)
### Slice 2: Deterministic Scanner (3h)
### Slice 3: Manifest Checksum (2h)
### Slice 4: Snapshot Persistence (3h)
### Slice 5: Integration Tests (2h)

**Total: ~12 hours**

---

## 4. Backend Module Structure

```
backend/src/main/java/ru/hgd/sdlc/
└── context/
    ├── ContextBuilder.java
    ├── ManifestHasher.java
    └── ContextValidation.java
└── snapshot/
    ├── RunSnapshotService.java
    └── RunSnapshotRepository.java
```

---

## 5. Proposed DB Schema

Add table:

- `run_snapshots`

Fields:

- flow/rule/skills raw + parsed + checksum
- initial git head
- context manifest checksum

---

## 6. Tests

1. Unit: context root validation.
2. Unit: lexical ordering stable.
3. Unit: checksum stable.
4. Integration: snapshot includes refs and checksums.

---

## 7. Definition of Done

1. Manifest checksum stable across repeated scans.
2. Snapshot written before node execution.
3. Snapshot includes raw + parsed artifacts.

---

## 8. Risks & Mitigations

| Риск | Контрмера |
|------|-----------|
| Скан больших репозиториев | Limit max files and depth |
| Нестабильный checksum | Strict ordering |

---

## Summary

T4 фиксирует входные факты выполнения и обеспечивает воспроизводимость run.
