# T2 Implementation Plan — Registry + Release Pipeline + Signed Release Provenance

## Overview

**Цель:** Создать registry и release pipeline для публикации flow и skill как **immutable packages** с **verifiable provenance**.

**Ключевой инвариант:** Runtime использует **release package**, а не raw Markdown repo.

---

## 1. Scope T2 для Phase 0

### Входит в scope

| Компонент | Описание |
|-----------|----------|
| Release Builder | Сборка immutable package из tagged Git commit |
| Package Structure | Формат release archive с IR, Markdown, checksums |
| Registry Service | Хранение metadata о releases, versions, dependencies |
| Lock File Generator | Создание deterministic lock файлов для install |
| Provenance Signer | Подписание release с верифицируемой provenance |
| Provenance Verifier | Проверка signatures при install |
| Package Resolver | Разрешение flow→skill dependencies |
| Registry API | REST API для publish/query/releases |

### НЕ входит в scope (Phase 0)

| Компонент | Причина |
|-----------|---------|
| Latest/floating versions | Только explicit versions для enterprise |
| Private registry auth | MVP: публичный или single-tenant |
| Package replication | Может быть добавлено позже |
| Web UI для catalog | T13 (Runner Console) покроет минимальный UI |
| Semantic versioning constraints | Только exact version matching |
| Dependency caching proxy | YAGNI для Phase 0 |
| Air-gapped registry support | Требует additional complexity |
| Package signing keys rotation | MVP: static key configuration |

---

## 2. Implementation Slices (рекомендуемый порядок)

### Slice 1: Registry Domain Models (2h)
### Slice 2: Release Package Structure (2h)
### Slice 3: Release Builder (3h)
### Slice 4: Registry Service + DB Schema (3h)
### Slice 5: Package Resolver (2h)
### Slice 6: Lock File Generator (2h)
### Slice 7: Provenance Models (1h)
### Slice 8: Provenance Signer (2h)
### Slice 9: Provenance Verifier (2h)
### Slice 10: Registry REST API (3h)
### Slice 11: Release CLI Commands (2h)
### Slice 12: Integration Tests (3h)

**Total: ~27 hours**

---

## 3. Backend Module Structure

```
backend/src/main/java/ru/hgd/sdlc/
├── registry/                                    # NEW module
│   ├── domain/
│   │   ├── model/
│   │   │   ├── release/
│   │   │   │   ├── ReleaseId.java              # UUID-based release ID
│   │   │   │   ├── ReleaseVersion.java         # Semantic version
│   │   │   │   ├── ReleasePackage.java         # Aggregate root
│   │   │   │   ├── ReleaseManifest.java        # release-manifest.json model
│   │   │   │   ├── ReleaseArtifact.java        # File in package
│   │   │   │   ├── ReleaseStatus.java          # PENDING, PUBLISHED, DEPRECATED
│   │   │   │   └── PackageChecksums.java       # SHA256 checksums map
│   │   │   │
│   │   │   ├── provenance/
│   │   │   │   ├── Provenance.java             # provenance.json model
│   │   │   │   ├── ProvenanceSignature.java    # Signature wrapper
│   │   │   │   ├── BuildIdentity.java          # Who/what built this
│   │   │   │   └── SourceReference.java        # Git repo + commit
│   │   │   │
│   │   │   ├── lockfile/
│   │   │   │   ├── FlowLockfile.java           # flow.lock.json model
│   │   │   │   ├── SkillLockEntry.java         # Skill resolution entry
│   │   │   │   └── LockfileChecksum.java       # Checksum entry
│   │   │   │
│   │   │   └── dependency/
│   │   │       ├── DependencyRef.java          # Reference to another package
│   │   │       ├── ResolvedDependency.java     # Resolved dependency
│   │   │       └── DependencyGraph.java        # Transitive dependencies
│   │   │
│   │   ├── release/
│   │   │   ├── ReleaseBuilder.java             # Domain service: build package
│   │   │   ├── PackageAssembler.java           # Assemble package files
│   │   │   └── ChecksumCalculator.java         # Compute checksums
│   │   │
│   │   ├── resolver/
│   │   │   ├── PackageResolver.java            # Resolve dependencies
│   │   │   └── DependencyResolver.java         # Resolve transitive deps
│   │   │
│   │   ├── lockfile/
│   │   │   ├── LockfileGenerator.java          # Generate lock file
│   │   │   └── LockfileSerializer.java         # JSON serialization
│   │   │
│   │   ├── provenance/
│   │   │   ├── ProvenanceBuilder.java          # Build provenance record
│   │   │   ├── ProvenanceSigner.java           # Sign provenance
│   │   │   ├── ProvenanceVerifier.java         # Verify provenance
│   │   │   └── SigningKeyProvider.java         # Key management interface
│   │   │
│   │   └── registry/
│   │       ├── RegistryRepository.java         # Domain repository interface
│   │       └── ReleaseQuery.java               # Query specification
│   │
│   ├── application/
│   │   ├── ReleaseService.java                 # Application facade
│   │   ├── RegistryService.java                # Registry operations
│   │   ├── PublishService.java                 # Publish workflow
│   │   ├── ResolveService.java                 # Dependency resolution
│   │   └── VerifyService.java                  # Provenance verification
│   │
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── RegistryRepositoryImpl.java     # JPA implementation
│   │   │   ├── ReleaseEntity.java              # JPA entity
│   │   │   ├── ReleaseArtifactEntity.java      # JPA entity
│   │   │   └── DependencyEntity.java           # JPA entity
│   │   │
│   │   ├── storage/
│   │   │   ├── PackageStorage.java             # Package binary storage
│   │   │   └── FileSystemPackageStorage.java   # Local FS implementation
│   │   │
│   │   ├── signing/
│   │   │   ├── Ed25519Signer.java              # Ed25519 implementation
│   │   │   └── FileBasedKeyProvider.java       # Keys from files
│   │   │
│   │   └── rest/
│   │       ├── RegistryController.java         # REST API
│   │       ├── ReleaseDto.java                 # DTO
│   │       └── PublishRequest.java             # Request model
│   │
│   └── config/
│       ├── RegistryConfiguration.java          # Spring config
│       └── RegistryProperties.java             # @ConfigurationProperties
│
└── installer/                                  # T4 - separate module
    └── ...
```

---

## 4. Proposed DB Schema

### Migration: V004__registry_tables.sql

```sql
-- Release registry
CREATE TABLE releases (
    id                  UUID PRIMARY KEY,
    package_type        VARCHAR(16) NOT NULL,           -- 'FLOW' or 'SKILL'
    package_id          VARCHAR(128) NOT NULL,          -- Flow ID or Skill ID
    version             VARCHAR(32) NOT NULL,           -- SemVer (1.2.3)
    version_major       SMALLINT NOT NULL,
    version_minor       SMALLINT NOT NULL,
    version_patch       SMALLINT NOT NULL,
    version_pre_release VARCHAR(64),                    -- Pre-release tag

    status              VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMP NOT NULL,
    deprecated_at       TIMESTAMP,
    deprecation_reason  TEXT,

    -- Source reference
    source_repo_url     VARCHAR(512),
    source_commit_sha   VARCHAR(64) NOT NULL,
    source_tag          VARCHAR(128),

    -- Provenance
    provenance_sha256   VARCHAR(64) NOT NULL,
    provenance_signature TEXT,                          -- Base64 encoded

    -- Package
    package_sha256      VARCHAR(64) NOT NULL,
    package_size_bytes  BIGINT NOT NULL,
    package_path        VARCHAR(512) NOT NULL,

    -- IR reference
    ir_sha256           VARCHAR(64) NOT NULL,
    ir_schema_version   INTEGER NOT NULL DEFAULT 1,

    -- Metadata
    compiler_version    VARCHAR(32) NOT NULL,
    build_identity      JSONB,

    UNIQUE(package_type, package_id, version)
);

CREATE INDEX idx_releases_package ON releases(package_type, package_id);
CREATE INDEX idx_releases_version ON releases(version_major, version_minor, version_patch);
CREATE INDEX idx_releases_status ON releases(status);

-- Release artifacts (files in package)
CREATE TABLE release_artifacts (
    id              UUID PRIMARY KEY,
    release_id      UUID NOT NULL REFERENCES releases(id) ON DELETE CASCADE,
    artifact_path   VARCHAR(512) NOT NULL,              -- Path within package
    artifact_type   VARCHAR(32) NOT NULL,               -- 'flow_ir', 'flow_md', 'phase_md', 'skill_ir', etc.
    sha256          VARCHAR(64) NOT NULL,
    size_bytes      BIGINT NOT NULL,

    UNIQUE(release_id, artifact_path)
);

CREATE INDEX idx_release_artifacts_release ON release_artifacts(release_id);

-- Dependencies (flow → skill references)
CREATE TABLE release_dependencies (
    id                  UUID PRIMARY KEY,
    release_id          UUID NOT NULL REFERENCES releases(id) ON DELETE CASCADE,
    dependency_type     VARCHAR(16) NOT NULL,           -- 'SKILL', 'FLOW'
    dependency_id       VARCHAR(128) NOT NULL,          -- Dependency package ID
    dependency_version  VARCHAR(32) NOT NULL,           -- Exact version required
    dependency_sha256   VARCHAR(64) NOT NULL,           -- Resolved IR checksum

    UNIQUE(release_id, dependency_id)
);

CREATE INDEX idx_release_dependencies_release ON release_dependencies(release_id);
CREATE INDEX idx_release_dependencies_dep ON release_dependencies(dependency_id, dependency_version);

-- Lock file snapshots (for install baselines)
CREATE TABLE lockfile_snapshots (
    id              UUID PRIMARY KEY,
    project_id      UUID NOT NULL,
    flow_release_id UUID NOT NULL REFERENCES releases(id),
    lockfile_sha256 VARCHAR(64) NOT NULL,
    lockfile_json   JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    is_active       BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_lockfile_snapshots_project ON lockfile_snapshots(project_id);
CREATE INDEX idx_lockfile_snapshots_active ON lockfile_snapshots(project_id, is_active);
```

---

## 5. Release Package Format

### 5.1 Package Structure

```
{flow-id}-{version}.zip
├── release-manifest.json        # Package metadata
├── provenance.json              # Signed provenance
├── checksums.sha256             # All file checksums
├── flow.ir.json                 # Compiled IR
├── flow.md                      # Canonical flow markdown
├── phases/
│   ├── setup.md
│   ├── develop.md
│   └── review.md
├── nodes/
│   ├── initialize.md
│   ├── implement.md
│   └── approve.md
├── artifacts/
│   └── artifact-templates.md
└── skills/
    └── {skill-id}/
        ├── skill.ir.json
        ├── skill.md
        └── provenance.json      # Skill provenance (if bundled)
```

### 5.2 release-manifest.json

```json
{
  "manifestVersion": 1,
  "packageType": "FLOW",
  "packageId": "code-generation-flow",
  "version": "1.2.3",
  "createdAt": "2026-03-12T10:30:00Z",

  "source": {
    "repositoryUrl": "https://github.com/org/sdlc-flows.git",
    "commitSha": "abc123def456...",
    "tag": "code-generation-flow@1.2.3",
    "branch": "main"
  },

  "build": {
    "compilerVersion": "1.0.0",
    "builtBy": "ci-pipeline",
    "buildId": "build-12345",
    "buildUrl": "https://ci.example.com/build/12345"
  },

  "artifacts": [
    {
      "path": "flow.ir.json",
      "type": "FLOW_IR",
      "sha256": "e3b0c44298fc1c149afbf4c8...",
      "sizeBytes": 12345
    },
    {
      "path": "flow.md",
      "type": "FLOW_MARKDOWN",
      "sha256": "...",
      "sizeBytes": 5678
    }
  ],

  "dependencies": [
    {
      "type": "SKILL",
      "packageId": "code-initializer-skill",
      "version": "2.0.0",
      "sha256": "..."
    }
  ],

  "checksums": {
    "packageSha256": "e3b0c44298fc1c149afbf4c8...",
    "irSha256": "...",
    "manifestSha256": "..."
  },

  "provenanceRef": {
    "path": "provenance.json",
    "sha256": "..."
  }
}
```

### 5.3 provenance.json

```json
{
  "provenanceVersion": 1,
  "releaseId": "550e8400-e29b-41d4-a716-446655440000",
  "packageType": "FLOW",
  "packageId": "code-generation-flow",
  "version": "1.2.3",

  "source": {
    "repositoryUrl": "https://github.com/org/sdlc-flows.git",
    "commitSha": "abc123def456...",
    "tag": "code-generation-flow@1.2.3",
    "commitMessage": "Release 1.2.3: Add new phase",
    "author": "developer@example.com",
    "committedAt": "2026-03-12T10:00:00Z"
  },

  "build": {
    "builtAt": "2026-03-12T10:30:00Z",
    "builderId": "ci-pipeline",
    "builderVersion": "2.0.0",
    "buildId": "build-12345",
    "buildUrl": "https://ci.example.com/build/12345"
  },

  "ir": {
    "irSchemaVersion": 1,
    "irSha256": "...",
    "compilerVersion": "1.0.0"
  },

  "checksums": {
    "packageSha256": "...",
    "irSha256": "...",
    "manifestSha256": "..."
  },

  "signature": {
    "algorithm": "Ed25519",
    "keyId": "key-2026-01",
    "publicKey": "MCowBQYDK2VwAyE...",
    "value": "Base64-encoded-signature..."
  }
}
```

### 5.4 checksums.sha256

```
# Release: code-generation-flow 1.2.3
# Generated: 2026-03-12T10:30:00Z

e3b0c44298fc1c149afbf4c8...  flow.ir.json
a7ffc6f8bf7ed62a1a5c3d8...  flow.md
b5d5b1f1e2d3c4b5a6f7e8...  phases/setup.md
c6e6c2f2e3d4c5b6a7f8e9...  phases/develop.md
...
```

---

## 6. Component Responsibilities

### 6.1 Release Builder

**Responsibility:** Собрать immutable release package из Git commit.

```
Input:
  - Git repository URL
  - Commit SHA or tag
  - Package type (FLOW/SKILL)

Process:
  1. Checkout at specified commit
  2. Parse and validate Markdown
  3. Compile IR using FlowCompiler/SkillCompiler
  4. Assemble package files
  5. Compute checksums
  6. Build release manifest

Output:
  - ReleasePackage (in-memory)
  - List<ReleaseArtifact>
  - ReleaseManifest
```

**Key Interface:**
```java
public interface ReleaseBuilder {
    ReleaseBuildResult build(ReleaseBuildRequest request);
}

public record ReleaseBuildRequest(
    GitRepository repository,
    String commitish,
    PackageType packageType,
    String packageId
) {}
```

### 6.2 Package Resolver

**Responsibility:** Разрешить flow→skill dependencies с exact version matching.

```
Input:
  - FlowRelease (or FlowIr)
  - RegistryRepository

Process:
  1. Extract skill references from IR
  2. Query registry for exact versions
  3. Build dependency graph
  4. Detect circular dependencies (error)
  5. Return resolved graph

Output:
  - DependencyGraph
  - List<ResolvedDependency>
```

**Key Interface:**
```java
public interface PackageResolver {
    DependencyGraph resolve(FlowRelease release);
    DependencyGraph resolveTransitive(List<DependencyRef> refs);
}
```

### 6.3 Lock File Generator

**Responsibility:** Создать deterministic lock file для install baseline.

```
Input:
  - FlowRelease
  - Resolved dependencies

Process:
  1. Create flow lock entry
  2. Create skill lock entries for each dependency
  3. Include checksums and provenance refs
  4. Compute lock file checksum

Output:
  - FlowLockfile (serializable to JSON)
```

**Lock File Format:**
```json
{
  "lockfileVersion": 1,
  "generatedAt": "2026-03-12T11:00:00Z",
  "generatorVersion": "1.0.0",

  "flow": {
    "packageId": "code-generation-flow",
    "version": "1.2.3",
    "releaseId": "...",
    "irSha256": "...",
    "packageSha256": "...",
    "provenanceSha256": "..."
  },

  "skills": [
    {
      "packageId": "code-initializer-skill",
      "version": "2.0.0",
      "releaseId": "...",
      "irSha256": "...",
      "packageSha256": "...",
      "provenanceSha256": "..."
    }
  ],

  "checksum": "sha256:..."
}
```

### 6.4 Provenance Signer

**Responsibility:** Подписать provenance record с cryptographic signature.

```
Input:
  - Provenance (unsigned)
  - SigningKey

Process:
  1. Canonicalize provenance JSON (sorted keys, no whitespace)
  2. Compute SHA256 of canonical JSON
  3. Sign hash with Ed25519 private key
  4. Attach signature to provenance

Output:
  - Provenance (signed)
```

**Key Interface:**
```java
public interface ProvenanceSigner {
    Provenance sign(Provenance unsigned, SigningKey key);
}

public interface SigningKeyProvider {
    KeyPair getKey(String keyId);
    String getCurrentKeyId();
}
```

### 6.5 Provenance Verifier

**Responsibility:** Верифицировать provenance signature перед install.

```
Input:
  - Provenance (signed)
  - TrustedPublicKeys

Process:
  1. Extract signature and key ID
  2. Look up trusted public key
  3. Canonicalize provenance (without signature)
  4. Verify Ed25519 signature
  5. Validate checksums match

Output:
  - VerificationResult (valid/invalid + reason)
```

**Key Interface:**
```java
public interface ProvenanceVerifier {
    VerificationResult verify(Provenance provenance);
    VerificationResult verify(PackageChecksums checksums, Provenance provenance);
}

public record VerificationResult(
    boolean valid,
    String keyId,
    String reason  // null if valid
) {}
```

---

## 7. Minimal API Endpoints for Phase 0

### 7.1 Registry API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/releases` | POST | Publish a release |
| `/api/v1/releases/{type}/{id}/versions` | GET | List versions for package |
| `/api/v1/releases/{type}/{id}/{version}` | GET | Get release metadata |
| `/api/v1/releases/{type}/{id}/{version}/package` | GET | Download package |
| `/api/v1/releases/{type}/{id}/{version}/provenance` | GET | Get provenance |
| `/api/v1/releases/{type}/{id}/{version}/verify` | POST | Verify provenance |
| `/api/v1/resolve` | POST | Resolve dependencies |
| `/api/v1/lockfile` | POST | Generate lock file |

### 7.2 API Request/Response Examples

**GET /api/v1/releases/flow/code-generation-flow/versions**
```json
{
  "packageId": "code-generation-flow",
  "versions": [
    {
      "version": "1.2.3",
      "publishedAt": "2026-03-12T10:30:00Z",
      "status": "PUBLISHED",
      "irSchemaVersion": 1
    },
    {
      "version": "1.2.2",
      "publishedAt": "2026-03-10T15:00:00Z",
      "status": "PUBLISHED",
      "irSchemaVersion": 1
    }
  ]
}
```

**POST /api/v1/resolve**
```json
// Request
{
  "flow": {
    "packageId": "code-generation-flow",
    "version": "1.2.3"
  }
}

// Response
{
  "resolved": {
    "flow": {
      "packageId": "code-generation-flow",
      "version": "1.2.3",
      "releaseId": "...",
      "irSha256": "...",
      "packageSha256": "..."
    },
    "skills": [
      {
        "packageId": "code-initializer-skill",
        "version": "2.0.0",
        "releaseId": "...",
        "irSha256": "...",
        "packageSha256": "..."
      }
    ]
  },
  "graphDepth": 1
}
```

---

## 8. Invariants & Acceptance Criteria

### 8.1 Core Invariants

| ID | Invariant | Verification |
|----|-----------|--------------|
| I1 | Same commit → same package SHA256 | Determinism test |
| I2 | No release mutation after publish | Immutable storage test |
| I3 | All releases have valid provenance | Signature verification test |
| I4 | IR checksum matches compiled IR | Checksum validation test |
| I5 | All dependencies resolvable | Dependency resolution test |
| I6 | Lock files are deterministic | Same inputs → same lockfile |
| I7 | Provenance signature verifiable | Public key verification |
| I8 | No duplicate (type, id, version) | DB unique constraint |
| I9 | Install uses release, not repo | Integration test |

### 8.2 Acceptance Criteria

**AC1: Release Builder**
- [ ] Build from Git tag succeeds
- [ ] Build from commit SHA succeeds
- [ ] Invalid Markdown → build fails with errors
- [ ] Same input → byte-identical output
- [ ] Package contains all required files

**AC2: Registry Service**
- [ ] Publish new release succeeds
- [ ] Duplicate publish fails gracefully
- [ ] Query by version returns correct release
- [ ] List versions returns sorted list

**AC3: Dependency Resolution**
- [ ] Resolve direct dependencies
- [ ] Resolve transitive dependencies
- [ ] Circular dependency → error
- [ ] Missing dependency → error with details

**AC4: Lock File Generator**
- [ ] Generate valid lock file JSON
- [ ] Lock file includes all checksums
- [ ] Lock file includes provenance refs
- [ ] Deterministic output for same inputs

**AC5: Provenance**
- [ ] Sign provenance with Ed25519
- [ ] Verify signature succeeds for valid
- [ ] Verify fails for tampered content
- [ ] Verify fails for unknown key

---

## 9. Risks & Open Questions

### 9.1 Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Git repository unavailable | Cannot build release | Cache source at build time |
| Signing key compromise | Provenance untrustable | Key rotation strategy (post-Phase 0) |
| IR schema evolution | Old releases unreadable | Schema versioning, backward compat |
| Large packages | Storage/cost issues | Size limits, artifact cleanup |
| Dependency explosion | Resolution performance | Depth limits, caching |

### 9.2 Open Questions (need decision before implementation)

| Question | Options | Recommendation |
|----------|---------|----------------|
| Signing algorithm | RSA-4096 vs Ed25519 vs ECDSA | Ed25519 (smaller, faster) |
| Package storage | S3 vs filesystem vs DB | Filesystem for Phase 0, S3 later |
| Key management | File-based vs Vault vs KMS | File-based for Phase 0 |
| Provenance format | Custom JSON vs SLSA | Custom JSON (simpler), SLSA later |
| Dependency version constraints | Exact only vs semver ranges | Exact only for Phase 0 |

---

## 10. Dependencies & Sequencing

### 10.1 Must Complete Before T2

| Prerequisite | Status | Notes |
|--------------|--------|-------|
| T1: Compiler | ✅ Done | FlowCompiler, SkillCompiler ready |
| Sha256 hashing | ✅ Done | shared/hashing/Sha256 |
| Result type | ✅ Done | shared/kernel/Result |
| DomainException | ✅ Done | shared/errors/DomainException |
| FlowIr, SkillIr models | ✅ Done | compiler/domain/model/ir/ |
| JSON serialization | ✅ Done | JsonIRSerializer |

### 10.2 Can Be Deferred After T2

| Component | Defer To | Reason |
|-----------|----------|--------|
| Web UI for catalog | T13 | CLI first |
| Package replication | Phase 1 | YAGNI |
| Private registry auth | Phase 1 | Single-tenant first |
| Latest version alias | Never | Enterprise requirement |
| Semantic versioning constraints | Phase 1 | Exact versions sufficient |

---

## 11. Recommended Implementation Order

### Phase A: Core Domain (Slices 1-3)
1. **Slice 1:** Registry domain models (ReleasePackage, ReleaseManifest, etc.)
2. **Slice 2:** Release package structure + serialization
3. **Slice 3:** Release builder (uses existing FlowCompiler)

### Phase B: Persistence & API (Slices 4-5, 10)
4. **Slice 4:** DB schema + RegistryRepository implementation
5. **Slice 10 (partial):** Basic REST API for CRUD

### Phase C: Resolution & Lock Files (Slices 5-6)
6. **Slice 5:** Package resolver + dependency graph
7. **Slice 6:** Lock file generator

### Phase D: Provenance (Slices 7-9)
8. **Slice 7:** Provenance models
9. **Slice 8:** Provenance signer (Ed25519)
10. **Slice 9:** Provenance verifier

### Phase E: CLI & Integration (Slices 11-12)
11. **Slice 11:** CLI commands (publish, list-releases, verify)
12. **Slice 12:** End-to-end integration tests

---

## 12. First 5 Slices for Development

### Slice 1: Registry Domain Models (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `registry/domain/model/release/ReleaseId.java` |
| CREATE | `registry/domain/model/release/ReleaseVersion.java` |
| CREATE | `registry/domain/model/release/ReleaseStatus.java` |
| CREATE | `registry/domain/model/release/ReleasePackage.java` |
| CREATE | `registry/domain/model/release/ReleaseManifest.java` |
| CREATE | `registry/domain/model/release/ReleaseArtifact.java` |
| CREATE | `registry/domain/model/release/PackageType.java` |
| CREATE | `registry/domain/model/release/PackageChecksums.java` |
| CREATE | `registry/domain/model/release/SourceReference.java` |
| CREATE | `registry/domain/model/release/BuildIdentity.java` |
| CREATE | `registry/domain/model/dependency/DependencyRef.java` |
| CREATE | `registry/domain/model/dependency/ResolvedDependency.java` |
| CREATE | `registry/domain/model/dependency/DependencyGraph.java` |

**Tests:**
- `ReleaseVersionTest.java` - SemVer parsing
- `ReleasePackageTest.java` - Construction
- `DependencyGraphTest.java` - Graph operations

---

### Slice 2: Release Package Structure (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `registry/domain/release/PackageStructure.java` |
| CREATE | `registry/domain/release/PackageWriter.java` |
| CREATE | `registry/domain/release/PackageReader.java` |
| CREATE | `registry/domain/release/ReleaseManifestSerializer.java` |

**Tests:**
- `PackageWriterTest.java` - Write package to zip
- `PackageReaderTest.java` - Read package from zip
- `PackageRoundTripTest.java` - Write → read → verify

---

### Slice 3: Release Builder (3h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `registry/domain/release/ReleaseBuilder.java` |
| CREATE | `registry/domain/release/PackageAssembler.java` |
| CREATE | `registry/domain/release/ChecksumCalculator.java` |
| CREATE | `registry/domain/release/ReleaseBuildRequest.java` |
| CREATE | `registry/domain/release/ReleaseBuildResult.java` |

**Dependencies:**
- Uses `FlowCompiler` from T1
- Uses `SkillCompiler` from T1

**Tests:**
- `ReleaseBuilderTest.java` - Build from test fixtures
- `ReleaseBuilderDeterminismTest.java` - Same input → same output

---

### Slice 4: Registry Service + DB Schema (3h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `V004__registry_tables.sql` |
| CREATE | `registry/domain/registry/RegistryRepository.java` |
| CREATE | `registry/domain/registry/ReleaseQuery.java` |
| CREATE | `registry/infrastructure/persistence/ReleaseEntity.java` |
| CREATE | `registry/infrastructure/persistence/ReleaseArtifactEntity.java` |
| CREATE | `registry/infrastructure/persistence/DependencyEntity.java` |
| CREATE | `registry/infrastructure/persistence/RegistryRepositoryImpl.java` |
| CREATE | `registry/application/RegistryService.java` |

**Tests:**
- `RegistryRepositoryTest.java` - CRUD operations
- `RegistryServiceTest.java` - Service layer

---

### Slice 5: Package Resolver (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `registry/domain/resolver/PackageResolver.java` |
| CREATE | `registry/domain/resolver/DependencyResolver.java` |
| CREATE | `registry/application/ResolveService.java` |

**Tests:**
- `PackageResolverTest.java` - Resolve dependencies
- `DependencyResolverTest.java` - Transitive resolution
- `CircularDependencyTest.java` - Cycle detection

---

## 13. Repository File Structure for T2

```
backend/
├── src/main/java/ru/hgd/sdlc/
│   └── registry/                              # NEW
│       ├── domain/
│       │   ├── model/
│       │   │   ├── release/
│       │   │   │   ├── ReleaseId.java
│       │   │   │   ├── ReleaseVersion.java
│       │   │   │   ├── ReleaseStatus.java
│       │   │   │   ├── ReleasePackage.java
│       │   │   │   ├── ReleaseManifest.java
│       │   │   │   ├── ReleaseArtifact.java
│       │   │   │   ├── PackageType.java
│       │   │   │   ├── PackageChecksums.java
│       │   │   │   ├── SourceReference.java
│       │   │   │   └── BuildIdentity.java
│       │   │   │
│       │   │   ├── provenance/
│       │   │   │   ├── Provenance.java
│       │   │   │   ├── ProvenanceSignature.java
│       │   │   │   └── ProvenanceVersion.java
│       │   │   │
│       │   │   ├── lockfile/
│       │   │   │   ├── FlowLockfile.java
│       │   │   │   ├── SkillLockEntry.java
│       │   │   │   └── LockfileVersion.java
│       │   │   │
│       │   │   └── dependency/
│       │   │       ├── DependencyRef.java
│       │   │       ├── ResolvedDependency.java
│       │   │       └── DependencyGraph.java
│       │   │
│       │   ├── release/
│       │   │   ├── ReleaseBuilder.java
│       │   │   ├── PackageAssembler.java
│       │   │   ├── ChecksumCalculator.java
│       │   │   └── PackageStructure.java
│       │   │
│       │   ├── resolver/
│       │   │   ├── PackageResolver.java
│       │   │   └── DependencyResolver.java
│       │   │
│       │   ├── lockfile/
│       │   │   ├── LockfileGenerator.java
│       │   │   └── LockfileSerializer.java
│       │   │
│       │   ├── provenance/
│       │   │   ├── ProvenanceBuilder.java
│       │   │   ├── ProvenanceSigner.java
│       │   │   ├── ProvenanceVerifier.java
│       │   │   └── SigningKeyProvider.java
│       │   │
│       │   └── registry/
│       │       ├── RegistryRepository.java
│       │       └── ReleaseQuery.java
│       │
│       ├── application/
│       │   ├── ReleaseService.java
│       │   ├── RegistryService.java
│       │   ├── PublishService.java
│       │   ├── ResolveService.java
│       │   └── VerifyService.java
│       │
│       ├── infrastructure/
│       │   ├── persistence/
│       │   │   ├── ReleaseEntity.java
│       │   │   ├── ReleaseArtifactEntity.java
│       │   │   ├── DependencyEntity.java
│       │   │   └── RegistryRepositoryImpl.java
│       │   │
│       │   ├── storage/
│       │   │   ├── PackageStorage.java
│       │   │   └── FileSystemPackageStorage.java
│       │   │
│       │   ├── signing/
│       │   │   ├── Ed25519Signer.java
│       │   │   └── FileBasedKeyProvider.java
│       │   │
│       │   └── rest/
│       │       ├── RegistryController.java
│       │       ├── ReleaseDto.java
│       │       ├── PublishRequest.java
│       │       └── ResolveRequest.java
│       │
│       └── config/
│           ├── RegistryConfiguration.java
│           └── RegistryProperties.java
│
├── src/main/resources/db/migration/
│   └── V004__registry_tables.sql              # NEW
│
└── src/test/java/ru/hgd/sdlc/registry/
    ├── domain/
    │   ├── model/
    │   │   ├── release/
    │   │   │   ├── ReleaseVersionTest.java
    │   │   │   ├── ReleasePackageTest.java
    │   │   │   └── PackageChecksumsTest.java
    │   │   │
    │   │   ├── provenance/
    │   │   │   ├── ProvenanceTest.java
    │   │   │   └── ProvenanceSignatureTest.java
    │   │   │
    │   │   ├── lockfile/
    │   │   │   └── FlowLockfileTest.java
    │   │   │
    │   │   └── dependency/
    │   │       ├── DependencyGraphTest.java
    │   │       └── ResolvedDependencyTest.java
    │   │
    │   ├── release/
    │   │   ├── ReleaseBuilderTest.java
    │   │   ├── ReleaseBuilderDeterminismTest.java
    │   │   ├── PackageAssemblerTest.java
    │   │   └── ChecksumCalculatorTest.java
    │   │
    │   ├── resolver/
    │   │   ├── PackageResolverTest.java
    │   │   └── DependencyResolverTest.java
    │   │
    │   ├── lockfile/
    │   │   ├── LockfileGeneratorTest.java
    │   │   └── LockfileDeterminismTest.java
    │   │
    │   └── provenance/
    │       ├── ProvenanceBuilderTest.java
    │       ├── ProvenanceSignerTest.java
    │       └── ProvenanceVerifierTest.java
    │
    ├── application/
    │   ├── ReleaseServiceTest.java
    │   ├── RegistryServiceTest.java
    │   └── ResolveServiceTest.java
    │
    ├── infrastructure/
    │   ├── persistence/
    │   │   └── RegistryRepositoryTest.java
    │   │
    │   ├── storage/
    │   │   └── FileSystemPackageStorageTest.java
    │   │
    │   ├── signing/
    │   │   ├── Ed25519SignerTest.java
    │   │   └── FileBasedKeyProviderTest.java
    │   │
    │   └── rest/
    │       └── RegistryControllerTest.java
    │
    └── integration/
        ├── ReleasePublishIntegrationTest.java
        ├── DependencyResolutionIntegrationTest.java
        ├── LockfileGenerationIntegrationTest.java
        └── ProvenanceVerificationIntegrationTest.java
```

---

## Summary

**T2 Scope:** Immutable release packages with verifiable provenance, registry storage, dependency resolution, lock file generation.

**Key Principles:**
1. Runtime uses release package, never raw Markdown
2. All releases are immutable after publish
3. Every release has signed provenance
4. Exact version matching (no latest/floating)
5. Deterministic packages (same input → same SHA256)

**Implementation Order:**
1. Registry domain models
2. Package structure
3. Release builder
4. DB schema + repository
5. Package resolver
6. Lock file generator
7. Provenance models
8. Provenance signer
9. Provenance verifier
10. REST API
11. CLI commands
12. Integration tests

**Total Effort:** ~27 hours
