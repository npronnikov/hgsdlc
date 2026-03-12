# T3 Implementation Plan — Metadata Indexer and Search API

## Overview

**Цель:** Создать metadata catalog для поиска и обнаружения flows и skills через индексацию release packages.

**Ключевой инвариант:** T3 работает **только с release packages и registry metadata** — НЕ с execution state или evidence.

---

## 1. Scope T3 для Phase 0

### Входит в scope

| Компонент | Описание |
|-----------|----------|
| Package Indexer | Индексация metadata из release packages при publish |
| Metadata Store | Хранение searchable metadata в БД |
| Search API | REST API для поиска flows/skills по различным критериям |
| Version Resolution | Получение конкретной версии или списка версий |
| Installation Resolution | Поиск flow по установленному проекту |
| Dependency Indexing | Индексация flow→skill dependencies для обратного поиска |

### НЕ входит в scope (Phase 0)

| Компонент | Причина |
|-----------|---------|
| Marketplace UI | T13 (Runner Console) покроет минимальный UI |
| Recommendations engine | Сложный ML/ranking — Phase 1+ |
| Semantic search | Требует embeddings, vector DB — Phase 1+ |
| Usage analytics | Требует интеграцию с runtime telemetry — Phase 1+ |
| Advanced ranking | Популярность, ratings — Phase 1+ |
| Full-text search on body | Достаточно metadata поиска — Phase 0 |
| Real-time index updates | Eventual consistency OK — Phase 0 |
| Tag autocompletion | YAGNI для MVP |
| Compatibility matrix | Сложные constraints — Phase 1+ |
| Private package filtering | Single-tenant first |

---

## 2. Conceptual Architecture

### 2.1 Data Flow

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Release        │────▶│  Package        │────▶│  Metadata       │
│  Publish (T2)   │     │  Indexer        │     │  Store (DB)     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                                        │
                        ┌─────────────────┐             │
                        │  Search API     │◀────────────┘
                        └─────────────────┘
                                │
                        ┌───────▼───────┐
                        │  REST Client  │
                        │  (Runner/UI)  │
                        └───────────────┘
```

### 2.2 Separation from T2

| Aspect | T2 Registry | T3 Metadata Index |
|--------|-------------|-------------------|
| Primary data | Release packages (IR, provenance) | Searchable metadata |
| Storage | Package binaries in CAS | Indexed fields in DB |
| Query pattern | Get by ID/version | Search by criteria |
| Mutability | Immutable packages | Mutable indexes |
| Purpose | Distribution, provenance | Discovery, search |

**Key insight:** T2 хранит packages, T3 индексирует их metadata для поиска.

---

## 3. Implementation Slices

### Slice 1: Metadata Domain Models (2h)
### Slice 2: DB Schema for Metadata Index (2h)
### Slice 3: Package Indexer Service (3h)
### Slice 4: Metadata Repository (2h)
### Slice 5: Search API — List Operations (3h)
### Slice 6: Search API — Filtering & Pagination (2h)
### Slice 7: Version Resolution API (2h)
### Slice 8: Installation Resolution (2h)
### Slice 9: Index Sync on Publish (2h)
### Slice 10: CLI Commands for Index (2h)
### Slice 11: Integration Tests (3h)

**Total: ~25 hours**

---

## 4. Backend Module Structure

```
backend/src/main/java/ru/hgd/sdlc/
├── catalog/                                     # NEW module (T3)
│   ├── domain/
│   │   ├── model/
│   │   │   ├── flow/
│   │   │   │   ├── FlowMetadata.java           # Searchable flow metadata
│   │   │   │   ├── FlowVersionInfo.java        # Version summary
│   │   │   │   ├── FlowSearchQuery.java        # Query specification
│   │   │   │   └── FlowSearchResult.java       # Search result
│   │   │   │
│   │   │   ├── skill/
│   │   │   │   ├── SkillMetadata.java          # Searchable skill metadata
│   │   │   │   ├── SkillVersionInfo.java       # Version summary
│   │   │   │   ├── SkillSearchQuery.java       # Query specification
│   │   │   │   └── SkillSearchResult.java      # Search result
│   │   │   │
│   │   │   ├── dependency/
│   │   │   │   ├── FlowSkillDependency.java    # Flow → Skill dependency record
│   │   │   │   └── ReverseDependencyQuery.java # Query for reverse deps
│   │   │   │
│   │   │   ├── installation/
│   │   │   │   ├── InstallationRecord.java     # Project installation
│   │   │   │   └── InstallationQuery.java      # Query specification
│   │   │   │
│   │   │   └── search/
│   │   │       ├── SearchCriteria.java         # Common search criteria
│   │   │       ├── SortSpec.java               # Sort specification
│   │   │       ├── PageRequest.java            # Pagination
│   │   │       └── SearchResult.java           # Generic result wrapper
│   │   │
│   │   ├── indexer/
│   │   │   ├── PackageIndexer.java             # Domain service: index package
│   │   │   ├── MetadataExtractor.java          # Extract metadata from release
│   │   │   └── IndexingResult.java             # Result of indexing
│   │   │
│   │   └── search/
│   │       ├── FlowSearchService.java          # Domain service: search flows
│   │       ├── SkillSearchService.java         # Domain service: search skills
│   │       └── InstallationResolver.java       # Resolve installations
│   │
│   ├── application/
│   │   ├── CatalogService.java                 # Application facade
│   │   ├── IndexingService.java                # Indexing operations
│   │   ├── FlowCatalogService.java             # Flow catalog operations
│   │   ├── SkillCatalogService.java            # Skill catalog operations
│   │   └── InstallationService.java            # Installation tracking
│   │
│   ├── infrastructure/
│   │   ├── persistence/
│   │   │   ├── FlowMetadataEntity.java         # JPA entity
│   │   │   ├── FlowVersionEntity.java          # JPA entity
│   │   │   ├── SkillMetadataEntity.java        # JPA entity
│   │   │   ├── SkillVersionEntity.java         # JPA entity
│   │   │   ├── FlowSkillDependencyEntity.java  # JPA entity
│   │   │   ├── InstallationEntity.java         # JPA entity
│   │   │   ├── FlowMetadataRepository.java     # Spring Data JPA
│   │   │   ├── SkillMetadataRepository.java    # Spring Data JPA
│   │   │   └── InstallationRepository.java     # Spring Data JPA
│   │   │
│   │   ├── indexer/
│   │   │   ├── ReleasePackageIndexer.java      # Index from ReleasePackage
│   │   │   └── MetadataExtractorImpl.java      # Extract from IR
│   │   │
│   │   └── rest/
│   │       ├── CatalogController.java          # REST API
│   │       ├── FlowCatalogController.java      # Flow-specific endpoints
│   │       ├── SkillCatalogController.java     # Skill-specific endpoints
│   │       ├── SearchRequest.java              # Request DTO
│   │       ├── FlowSearchResponse.java         # Response DTO
│   │       └── SkillSearchResponse.java        # Response DTO
│   │
│   └── config/
│       ├── CatalogConfiguration.java           # Spring config
│       └── CatalogProperties.java              # @ConfigurationProperties
```

---

## 5. Proposed DB Schema

### Migration: V005__catalog_tables.sql

```sql
-- ============================================
-- Flow Metadata Index
-- ============================================

-- Flow-level metadata (one row per flow, aggregates across versions)
CREATE TABLE flow_metadata (
    id                  UUID PRIMARY KEY,
    flow_id             VARCHAR(128) NOT NULL UNIQUE,  -- Canonical flow ID (e.g., "code-generation-flow")

    -- Searchable fields
    display_name        VARCHAR(255) NOT NULL,
    description         TEXT,
    author              VARCHAR(255) NOT NULL,

    -- Indexing metadata
    first_published_at  TIMESTAMP NOT NULL,
    last_published_at   TIMESTAMP NOT NULL,
    version_count       INTEGER NOT NULL DEFAULT 1,
    latest_version      VARCHAR(32) NOT NULL,

    -- Search tags (for filtering)
    tags                JSONB DEFAULT '[]'::jsonb,

    -- Status
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, DEPRECATED

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_flow_metadata_flow_id ON flow_metadata(flow_id);
CREATE INDEX idx_flow_metadata_display_name ON flow_metadata(display_name);
CREATE INDEX idx_flow_metadata_author ON flow_metadata(author);
CREATE INDEX idx_flow_metadata_tags ON flow_metadata USING GIN(tags);
CREATE INDEX idx_flow_metadata_status ON flow_metadata(status);

-- ============================================
-- Flow Version Index
-- ============================================

-- Per-version metadata (one row per version)
CREATE TABLE flow_versions (
    id                  UUID PRIMARY KEY,
    flow_metadata_id    UUID NOT NULL REFERENCES flow_metadata(id) ON DELETE CASCADE,

    -- Version info
    version             VARCHAR(32) NOT NULL,           -- SemVer
    version_major       SMALLINT NOT NULL,
    version_minor       SMALLINT NOT NULL,
    version_patch       SMALLINT NOT NULL,
    version_pre_release VARCHAR(64),

    -- Searchable fields
    display_name        VARCHAR(255) NOT NULL,
    description         TEXT,
    author              VARCHAR(255) NOT NULL,

    -- Release reference (links back to T2 registry)
    release_id          UUID NOT NULL,                  -- Reference to releases table in T2
    ir_sha256           VARCHAR(64) NOT NULL,
    ir_schema_version   INTEGER NOT NULL DEFAULT 1,

    -- Source reference
    source_repo_url     VARCHAR(512),
    source_commit_sha   VARCHAR(64) NOT NULL,
    source_tag          VARCHAR(128),

    -- Status
    status              VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',  -- PUBLISHED, DEPRECATED

    -- Phase/node summary (for search)
    phase_count         INTEGER NOT NULL DEFAULT 0,
    node_count          INTEGER NOT NULL DEFAULT 0,
    gate_count          INTEGER NOT NULL DEFAULT 0,

    published_at        TIMESTAMP NOT NULL,
    deprecated_at       TIMESTAMP,

    UNIQUE(flow_metadata_id, version)
);

CREATE INDEX idx_flow_versions_flow_metadata ON flow_versions(flow_metadata_id);
CREATE INDEX idx_flow_versions_version ON flow_versions(version_major, version_minor, version_patch);
CREATE INDEX idx_flow_versions_status ON flow_versions(status);
CREATE INDEX idx_flow_versions_published ON flow_versions(published_at DESC);
CREATE INDEX idx_flow_versions_release ON flow_versions(release_id);

-- ============================================
-- Skill Metadata Index
-- ============================================

-- Skill-level metadata (one row per skill, aggregates across versions)
CREATE TABLE skill_metadata (
    id                  UUID PRIMARY KEY,
    skill_id            VARCHAR(128) NOT NULL UNIQUE,   -- Canonical skill ID

    -- Searchable fields
    display_name        VARCHAR(255) NOT NULL,
    description         TEXT,
    author              VARCHAR(255) NOT NULL,

    -- Indexing metadata
    first_published_at  TIMESTAMP NOT NULL,
    last_published_at   TIMESTAMP NOT NULL,
    version_count       INTEGER NOT NULL DEFAULT 1,
    latest_version      VARCHAR(32) NOT NULL,

    -- Search tags
    tags                JSONB DEFAULT '[]'::jsonb,

    -- Handler info (for filtering)
    handler_kind        VARCHAR(32),                    -- e.g., "qwen-coder", "script"

    -- Status
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_skill_metadata_skill_id ON skill_metadata(skill_id);
CREATE INDEX idx_skill_metadata_display_name ON skill_metadata(display_name);
CREATE INDEX idx_skill_metadata_author ON skill_metadata(author);
CREATE INDEX idx_skill_metadata_tags ON skill_metadata USING GIN(tags);
CREATE INDEX idx_skill_metadata_handler ON skill_metadata(handler_kind);
CREATE INDEX idx_skill_metadata_status ON skill_metadata(status);

-- ============================================
-- Skill Version Index
-- ============================================

-- Per-version metadata (one row per version)
CREATE TABLE skill_versions (
    id                  UUID PRIMARY KEY,
    skill_metadata_id   UUID NOT NULL REFERENCES skill_metadata(id) ON DELETE CASCADE,

    -- Version info
    version             VARCHAR(32) NOT NULL,
    version_major       SMALLINT NOT NULL,
    version_minor       SMALLINT NOT NULL,
    version_patch       SMALLINT NOT NULL,
    version_pre_release VARCHAR(64),

    -- Searchable fields
    display_name        VARCHAR(255) NOT NULL,
    description         TEXT,
    author              VARCHAR(255) NOT NULL,

    -- Release reference
    release_id          UUID NOT NULL,
    ir_sha256           VARCHAR(64) NOT NULL,
    ir_schema_version   INTEGER NOT NULL DEFAULT 1,

    -- Source reference
    source_repo_url     VARCHAR(512),
    source_commit_sha   VARCHAR(64) NOT NULL,
    source_tag          VARCHAR(128),

    -- Handler info
    handler_kind        VARCHAR(32),
    handler_ref         VARCHAR(512),

    -- Status
    status              VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',

    published_at        TIMESTAMP NOT NULL,
    deprecated_at       TIMESTAMP,

    UNIQUE(skill_metadata_id, version)
);

CREATE INDEX idx_skill_versions_skill_metadata ON skill_versions(skill_metadata_id);
CREATE INDEX idx_skill_versions_version ON skill_versions(version_major, version_minor, version_patch);
CREATE INDEX idx_skill_versions_status ON skill_versions(status);
CREATE INDEX idx_skill_versions_published ON skill_versions(published_at DESC);

-- ============================================
-- Flow → Skill Dependencies Index
-- ============================================

-- Tracks which skills each flow version depends on
-- Enables reverse dependency queries ("which flows use this skill?")
CREATE TABLE flow_skill_dependencies (
    id                  UUID PRIMARY KEY,
    flow_version_id     UUID NOT NULL REFERENCES flow_versions(id) ON DELETE CASCADE,
    skill_id            VARCHAR(128) NOT NULL,          -- Skill ID (not version-specific)
    skill_version       VARCHAR(32) NOT NULL,           -- Exact version used

    -- For quick lookups
    skill_metadata_id   UUID REFERENCES skill_metadata(id) ON DELETE SET NULL,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(flow_version_id, skill_id)
);

CREATE INDEX idx_flow_skill_dep_flow ON flow_skill_dependencies(flow_version_id);
CREATE INDEX idx_flow_skill_dep_skill ON flow_skill_dependencies(skill_id, skill_version);
CREATE INDEX idx_flow_skill_dep_skill_meta ON flow_skill_dependencies(skill_metadata_id);

-- ============================================
-- Installation Tracking
-- ============================================

-- Tracks which projects have which flows installed
-- Enables "find installation" queries
CREATE TABLE installations (
    id                  UUID PRIMARY KEY,
    project_id          UUID NOT NULL,                  -- External project reference
    project_name        VARCHAR(255),

    -- Flow reference
    flow_id             VARCHAR(128) NOT NULL,          -- Canonical flow ID
    flow_version        VARCHAR(32) NOT NULL,           -- Installed version
    flow_metadata_id    UUID REFERENCES flow_metadata(id) ON DELETE SET NULL,

    -- Lock file reference
    lockfile_sha256     VARCHAR(64),

    -- Installation metadata
    installed_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    installed_by        VARCHAR(255) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT true,

    UNIQUE(project_id, flow_id)
);

CREATE INDEX idx_installations_project ON installations(project_id);
CREATE INDEX idx_installations_flow ON installations(flow_id, flow_version);
CREATE INDEX idx_installations_active ON installations(project_id, is_active);

-- ============================================
-- Compatibility Tags (for future use)
-- ============================================

-- Stores compatibility information (e.g., "requires-agent:qwen-coder")
-- Currently minimal, will expand in Phase 1
CREATE TABLE compatibility_tags (
    id                  UUID PRIMARY KEY,
    entity_type         VARCHAR(16) NOT NULL,           -- 'FLOW' or 'SKILL'
    entity_id           UUID NOT NULL,                  -- Reference to flow_versions or skill_versions
    tag_key             VARCHAR(64) NOT NULL,           -- e.g., "requires-agent"
    tag_value           VARCHAR(255) NOT NULL,          -- e.g., "qwen-coder"

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE(entity_type, entity_id, tag_key)
);

CREATE INDEX idx_compat_tags_entity ON compatibility_tags(entity_type, entity_id);
CREATE INDEX idx_compat_tags_key_value ON compatibility_tags(tag_key, tag_value);
```

---

## 6. Metadata Extraction from Release Package

### 6.1 Flow Metadata Extraction

```java
public interface MetadataExtractor {
    FlowMetadata extractFlowMetadata(ReleasePackage releasePackage);
    SkillMetadata extractSkillMetadata(SkillIr skillIr, ReleaseMetadata releaseMetadata);
}
```

### 6.2 Extracted Fields

| Source Field | Target Field | Notes |
|--------------|--------------|-------|
| FlowIr.flowId().value() | flow_metadata.flow_id | Canonical ID |
| FlowIr.metadata().displayName() | flow_metadata.display_name | Human-readable name |
| FlowIr.description() (body) | flow_metadata.description | Truncated to 1000 chars |
| ReleaseMetadata.author() | flow_metadata.author | |
| ReleaseMetadata.createdAt() | first_published_at / last_published_at | |
| FlowIr.phaseOrder().size() | flow_versions.phase_count | |
| FlowIr.nodeIndex().size() | flow_versions.node_count | |
| Count of GATE nodes | flow_versions.gate_count | |
| FlowIr.tags() | flow_metadata.tags | JSONB array |

### 6.3 Dependency Extraction

```java
// From FlowIr, extract skill references
Set<SkillRef> skillRefs = flowIr.resolvedSkills().keySet();

// Create dependency records
for (SkillRef ref : skillRefs) {
    FlowSkillDependency dep = FlowSkillDependency.builder()
        .flowVersionId(flowVersionId)
        .skillId(ref.skillId().value())
        .skillVersion(ref.version().value())
        .build();
}
```

---

## 7. Searchable Fields & Indexes

### 7.1 Flow Search Fields

| Field | Type | Indexed | Search Type |
|-------|------|---------|-------------|
| flow_id | VARCHAR | YES (unique) | Exact match |
| display_name | VARCHAR | YES (btree) | LIKE prefix |
| description | TEXT | NO | Full-text (Phase 1) |
| author | VARCHAR | YES (btree) | Exact/LIKE |
| tags | JSONB | YES (GIN) | Array contains |
| status | VARCHAR | YES (btree) | Exact |
| latest_version | VARCHAR | NO | Display only |
| version_count | INTEGER | NO | Display only |
| phase_count | INTEGER | NO | Sort/filter |
| published_at | TIMESTAMP | YES (desc) | Range/sort |

### 7.2 Skill Search Fields

| Field | Type | Indexed | Search Type |
|-------|------|---------|-------------|
| skill_id | VARCHAR | YES (unique) | Exact match |
| display_name | VARCHAR | YES (btree) | LIKE prefix |
| description | TEXT | NO | Full-text (Phase 1) |
| author | VARCHAR | YES (btree) | Exact/LIKE |
| tags | JSONB | YES (GIN) | Array contains |
| handler_kind | VARCHAR | YES (btree) | Exact |
| status | VARCHAR | YES (btree) | Exact |

### 7.3 Performance Indexes

```sql
-- Critical for search performance
CREATE INDEX idx_flow_metadata_search ON flow_metadata(status, display_name varchar_pattern_ops);
CREATE INDEX idx_skill_metadata_search ON skill_metadata(status, display_name varchar_pattern_ops);

-- For version listing (ordered by version descending)
CREATE INDEX idx_flow_versions_latest ON flow_versions(flow_metadata_id, version_major DESC, version_minor DESC, version_patch DESC);

-- For reverse dependency queries
CREATE INDEX idx_flow_skill_dep_reverse ON flow_skill_dependencies(skill_id);
```

---

## 8. Minimal Search API

### 8.1 Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/catalog/flows` | GET | List/search flows |
| `/api/v1/catalog/flows/{flowId}` | GET | Get flow metadata |
| `/api/v1/catalog/flows/{flowId}/versions` | GET | List versions |
| `/api/v1/catalog/flows/{flowId}/versions/{version}` | GET | Get version metadata |
| `/api/v1/catalog/skills` | GET | List/search skills |
| `/api/v1/catalog/skills/{skillId}` | GET | Get skill metadata |
| `/api/v1/catalog/skills/{skillId}/versions` | GET | List versions |
| `/api/v1/catalog/skills/{skillId}/versions/{version}` | GET | Get version metadata |
| `/api/v1/catalog/skills/{skillId}/dependents` | GET | Find flows using this skill |
| `/api/v1/catalog/installations/{projectId}` | GET | Resolve installation for project |
| `/api/v1/catalog/search` | GET | Global search (flows + skills) |

### 8.2 Query Parameters

**List Flows (`GET /api/v1/catalog/flows`):**

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search in display_name |
| `author` | string | Filter by author |
| `tag` | string[] | Filter by tags (AND) |
| `status` | string | ACTIVE, DEPRECATED |
| `sort` | string | name, published, version_count |
| `order` | string | asc, desc |
| `page` | int | Page number (0-indexed) |
| `size` | int | Page size (default 20, max 100) |

**Response:**
```json
{
  "flows": [
    {
      "flowId": "code-generation-flow",
      "displayName": "Code Generation Flow",
      "description": "Enterprise flow for AI-assisted code generation...",
      "author": "sdlc-team@example.com",
      "latestVersion": "1.2.3",
      "versionCount": 5,
      "phaseCount": 3,
      "tags": ["enterprise", "code-generation"],
      "status": "ACTIVE",
      "lastPublishedAt": "2026-03-12T10:30:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

**Get Version (`GET /api/v1/catalog/flows/{flowId}/versions/{version}`):**
```json
{
  "flowId": "code-generation-flow",
  "version": "1.2.3",
  "displayName": "Code Generation Flow",
  "description": "Full description...",
  "author": "sdlc-team@example.com",
  "releaseId": "550e8400-e29b-41d4-a716-446655440000",
  "irSha256": "e3b0c44298fc1c149...",
  "irSchemaVersion": 1,
  "source": {
    "repositoryUrl": "https://github.com/org/sdlc-flows.git",
    "commitSha": "abc123def456",
    "tag": "code-generation-flow@1.2.3"
  },
  "summary": {
    "phaseCount": 3,
    "nodeCount": 12,
    "gateCount": 2
  },
  "dependencies": [
    {
      "skillId": "code-initializer-skill",
      "version": "2.0.0"
    }
  ],
  "status": "PUBLISHED",
  "publishedAt": "2026-03-12T10:30:00Z"
}
```

**Resolve Installation (`GET /api/v1/catalog/installations/{projectId}`):**
```json
{
  "projectId": "550e8400-e29b-41d4-a716-446655440001",
  "flow": {
    "flowId": "code-generation-flow",
    "version": "1.2.3",
    "displayName": "Code Generation Flow"
  },
  "lockfileSha256": "e3b0c44298fc1c149...",
  "installedAt": "2026-03-10T15:00:00Z",
  "installedBy": "developer@example.com",
  "isActive": true
}
```

**Skill Dependents (`GET /api/v1/catalog/skills/{skillId}/dependents`):**
```json
{
  "skillId": "code-initializer-skill",
  "dependents": [
    {
      "flowId": "code-generation-flow",
      "flowDisplayName": "Code Generation Flow",
      "versionUsing": "1.2.3",
      "skillVersionUsed": "2.0.0"
    },
    {
      "flowId": "code-review-flow",
      "flowDisplayName": "Code Review Flow",
      "versionUsing": "0.5.0",
      "skillVersionUsed": "1.8.0"
    }
  ],
  "totalDependents": 2
}
```

---

## 9. T2 ↔ T3 Integration

### 9.1 Integration Points

```
┌──────────────────────────────────────────────────────────────┐
│                     T2 Registry Service                       │
│                                                              │
│  publishRelease(ReleasePackage)                              │
│         │                                                    │
│         ▼                                                    │
│  1. Store package in storage                                 │
│  2. Persist to releases table                                │
│  3. ┌─────────────────────────────────────────┐             │
│    │ Call T3 CatalogService.indexRelease()    │             │
│    │ with extracted metadata                  │             │
│    └─────────────────────────────────────────┘             │
│                                                              │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     T3 Catalog Service                        │
│                                                              │
│  indexRelease(ReleasePackage, ReleaseMetadata)               │
│         │                                                    │
│         ▼                                                    │
│  1. Extract metadata via MetadataExtractor                   │
│  2. Upsert flow_metadata / skill_metadata                    │
│  3. Insert flow_versions / skill_versions                    │
│  4. Index dependencies in flow_skill_dependencies            │
│  5. Return IndexingResult                                    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 9.2 Interface Contract

```java
// In T3 catalog/application/
public interface CatalogIndexingApi {
    /**
     * Called by T2 Registry when a new release is published.
     * Indexes the release metadata for search.
     */
    IndexingResult indexRelease(ReleasePackage releasePackage);

    /**
     * Called when a release is deprecated.
     * Updates status in the index.
     */
    void deprecateRelease(ReleaseId releaseId, String reason);
}
```

### 9.3 Eventual Consistency Note

For Phase 0, T3 indexing happens **synchronously** during T2 publish.
In Phase 1+, this can become **asynchronous** via event bus for scalability.

---

## 10. CLI Commands

### 10.1 Catalog CLI

```bash
# Search flows
sdlc catalog search flows "code-gen" --author "team@example.com" --tag enterprise

# List all flows
sdlc catalog list flows --status ACTIVE

# Get flow details
sdlc catalog get flow code-generation-flow

# List versions
sdlc catalog versions flow code-generation-flow

# Get specific version
sdlc catalog get flow code-generation-flow --version 1.2.3

# Find skill dependents
sdlc catalog dependents skill code-initializer-skill

# Resolve installation
sdlc catalog resolve installation --project-id <uuid>

# Reindex (admin)
sdlc catalog reindex --flow code-generation-flow
```

---

## 11. Phase 0 vs Phase 1+ Decisions

| Feature | Phase 0 | Phase 1+ |
|---------|---------|----------|
| Indexing timing | Synchronous | Async via events |
| Search type | Metadata only | Full-text + semantic |
| Ranking | Alphabetical/date | Popularity, relevance |
| Usage tracking | None | Telemetry-based |
| Compatibility | Basic tags | Full matrix |
| Private packages | No | Yes |
| Web UI | None (CLI only) | Catalog browser |
| Cache | None | Redis cache |
| Rate limiting | None | Yes |

---

## 12. Definition of Done

### T3.1 Metadata extraction
- [ ] FlowMetadata extracted from ReleasePackage
- [ ] SkillMetadata extracted from SkillIr
- [ ] Dependencies extracted and indexed

### T3.2 DB Schema
- [ ] flow_metadata table created
- [ ] flow_versions table created
- [ ] skill_metadata table created
- [ ] skill_versions table created
- [ ] flow_skill_dependencies table created
- [ ] installations table created
- [ ] All indexes created

### T3.3 Package Indexer
- [ ] Indexes new releases on publish
- [ ] Updates existing flow metadata on new version
- [ ] Handles skill indexing

### T3.4 Search API
- [ ] List flows with filtering works
- [ ] List skills with filtering works
- [ ] Get version metadata works
- [ ] Pagination works correctly
- [ ] Sorting works correctly

### T3.5 Version Resolution
- [ ] Get latest version works
- [ ] Get specific version works
- [ ] Version list ordered correctly

### T3.6 Installation Resolution
- [ ] Resolve by project ID works
- [ ] Returns active installation

### T3.7 T2 Integration
- [ ] Indexing triggered on publish
- [ ] Metadata consistent with release

---

## 13. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Index out of sync with registry | Stale search results | Reindex command, health checks |
| Large metadata volume | Slow queries | Pagination, indexes, archiving |
| Missing metadata fields | Incomplete search | Validation in extractor |
| Search performance | Poor UX | Indexes, query optimization |
| DB schema migration issues | Deployment failure | Backward-compatible migrations |

---

## 14. Recommended Implementation Order

### Phase A: Domain & Schema (Slices 1-2)
1. **Slice 1:** Metadata domain models
2. **Slice 2:** DB schema + migrations

### Phase B: Indexing (Slices 3-4)
3. **Slice 3:** Package indexer service
4. **Slice 4:** Metadata repository

### Phase C: Search API (Slices 5-8)
5. **Slice 5:** List flows/skills (basic)
6. **Slice 6:** Filtering & pagination
7. **Slice 7:** Version resolution
8. **Slice 8:** Installation resolution

### Phase D: Integration (Slices 9-11)
9. **Slice 9:** T2 integration (index on publish)
10. **Slice 10:** CLI commands
11. **Slice 11:** Integration tests

---

## 15. First 5 Slices for Implementation

### Slice 1: Metadata Domain Models (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `catalog/domain/model/flow/FlowMetadata.java` |
| CREATE | `catalog/domain/model/flow/FlowVersionInfo.java` |
| CREATE | `catalog/domain/model/flow/FlowSearchQuery.java` |
| CREATE | `catalog/domain/model/flow/FlowSearchResult.java` |
| CREATE | `catalog/domain/model/skill/SkillMetadata.java` |
| CREATE | `catalog/domain/model/skill/SkillVersionInfo.java` |
| CREATE | `catalog/domain/model/skill/SkillSearchQuery.java` |
| CREATE | `catalog/domain/model/skill/SkillSearchResult.java` |
| CREATE | `catalog/domain/model/dependency/FlowSkillDependency.java` |
| CREATE | `catalog/domain/model/installation/InstallationRecord.java` |
| CREATE | `catalog/domain/model/search/SearchCriteria.java` |
| CREATE | `catalog/domain/model/search/SortSpec.java` |
| CREATE | `catalog/domain/model/search/PageRequest.java` |
| CREATE | `catalog/domain/model/search/SearchResult.java` |

**Tests:**
- `FlowMetadataTest.java`
- `SkillMetadataTest.java`
- `SearchCriteriaTest.java`

---

### Slice 2: DB Schema for Metadata Index (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `V005__catalog_tables.sql` |

**Contents:** Full schema from Section 5 above.

**Tests:**
- Schema migration test (Testcontainers)

---

### Slice 3: Package Indexer Service (3h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `catalog/domain/indexer/PackageIndexer.java` |
| CREATE | `catalog/domain/indexer/MetadataExtractor.java` |
| CREATE | `catalog/domain/indexer/IndexingResult.java` |
| CREATE | `catalog/infrastructure/indexer/ReleasePackageIndexer.java` |
| CREATE | `catalog/infrastructure/indexer/MetadataExtractorImpl.java` |

**Dependencies:**
- Uses `ReleasePackage` from T2 registry
- Uses `FlowIr` from T1 compiler

**Tests:**
- `MetadataExtractorTest.java` — Extract from test release package
- `PackageIndexerTest.java` — Full indexing flow

---

### Slice 4: Metadata Repository (2h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `catalog/infrastructure/persistence/FlowMetadataEntity.java` |
| CREATE | `catalog/infrastructure/persistence/FlowVersionEntity.java` |
| CREATE | `catalog/infrastructure/persistence/SkillMetadataEntity.java` |
| CREATE | `catalog/infrastructure/persistence/SkillVersionEntity.java` |
| CREATE | `catalog/infrastructure/persistence/FlowSkillDependencyEntity.java` |
| CREATE | `catalog/infrastructure/persistence/InstallationEntity.java` |
| CREATE | `catalog/infrastructure/persistence/FlowMetadataRepository.java` |
| CREATE | `catalog/infrastructure/persistence/SkillMetadataRepository.java` |
| CREATE | `catalog/infrastructure/persistence/InstallationRepository.java` |

**Tests:**
- `FlowMetadataRepositoryTest.java`
- `SkillMetadataRepositoryTest.java`

---

### Slice 5: Search API — List Operations (3h)

**Files to create:**

| Action | File |
|--------|------|
| CREATE | `catalog/domain/search/FlowSearchService.java` |
| CREATE | `catalog/domain/search/SkillSearchService.java` |
| CREATE | `catalog/application/CatalogService.java` |
| CREATE | `catalog/application/FlowCatalogService.java` |
| CREATE | `catalog/application/SkillCatalogService.java` |
| CREATE | `catalog/infrastructure/rest/CatalogController.java` |
| CREATE | `catalog/infrastructure/rest/FlowCatalogController.java` |
| CREATE | `catalog/infrastructure/rest/SkillCatalogController.java` |
| CREATE | `catalog/infrastructure/rest/SearchRequest.java` |
| CREATE | `catalog/infrastructure/rest/FlowSearchResponse.java` |
| CREATE | `catalog/infrastructure/rest/SkillSearchResponse.java` |

**Tests:**
- `FlowCatalogControllerTest.java`
- `SkillCatalogControllerTest.java`
- `CatalogServiceTest.java`

---

## 16. Repository File Structure for T3

```
backend/
├── src/main/java/ru/hgd/sdlc/
│   └── catalog/                                 # NEW
│       ├── domain/
│       │   ├── model/
│       │   │   ├── flow/
│       │   │   │   ├── FlowMetadata.java
│       │   │   │   ├── FlowVersionInfo.java
│       │   │   │   ├── FlowSearchQuery.java
│       │   │   │   └── FlowSearchResult.java
│       │   │   │
│       │   │   ├── skill/
│       │   │   │   ├── SkillMetadata.java
│       │   │   │   ├── SkillVersionInfo.java
│       │   │   │   ├── SkillSearchQuery.java
│       │   │   │   └── SkillSearchResult.java
│       │   │   │
│       │   │   ├── dependency/
│       │   │   │   ├── FlowSkillDependency.java
│       │   │   │   └── ReverseDependencyQuery.java
│       │   │   │
│       │   │   ├── installation/
│       │   │   │   ├── InstallationRecord.java
│       │   │   │   └── InstallationQuery.java
│       │   │   │
│       │   │   └── search/
│       │   │       ├── SearchCriteria.java
│       │   │       ├── SortSpec.java
│       │   │       ├── PageRequest.java
│       │   │       └── SearchResult.java
│       │   │
│       │   ├── indexer/
│       │   │   ├── PackageIndexer.java
│       │   │   ├── MetadataExtractor.java
│       │   │   └── IndexingResult.java
│       │   │
│       │   └── search/
│       │       ├── FlowSearchService.java
│       │       ├── SkillSearchService.java
│       │       └── InstallationResolver.java
│       │
│       ├── application/
│       │   ├── CatalogService.java
│       │   ├── IndexingService.java
│       │   ├── FlowCatalogService.java
│       │   ├── SkillCatalogService.java
│       │   └── InstallationService.java
│       │
│       ├── infrastructure/
│       │   ├── persistence/
│       │   │   ├── FlowMetadataEntity.java
│       │   │   ├── FlowVersionEntity.java
│       │   │   ├── SkillMetadataEntity.java
│       │   │   ├── SkillVersionEntity.java
│       │   │   ├── FlowSkillDependencyEntity.java
│       │   │   ├── InstallationEntity.java
│       │   │   ├── FlowMetadataRepository.java
│       │   │   ├── SkillMetadataRepository.java
│       │   │   └── InstallationRepository.java
│       │   │
│       │   ├── indexer/
│       │   │   ├── ReleasePackageIndexer.java
│       │   │   └── MetadataExtractorImpl.java
│       │   │
│       │   └── rest/
│       │       ├── CatalogController.java
│       │       ├── FlowCatalogController.java
│       │       ├── SkillCatalogController.java
│       │       ├── SearchRequest.java
│       │       ├── FlowSearchResponse.java
│       │       └── SkillSearchResponse.java
│       │
│       └── config/
│           ├── CatalogConfiguration.java
│           └── CatalogProperties.java
│
├── src/main/resources/db/migration/
│   └── V005__catalog_tables.sql               # NEW
│
└── src/test/java/ru/hgd/sdlc/catalog/
    ├── domain/
    │   ├── model/
    │   │   ├── flow/
    │   │   │   └── FlowMetadataTest.java
    │   │   │
    │   │   ├── skill/
    │   │   │   └── SkillMetadataTest.java
    │   │   │
    │   │   └── search/
    │   │       └── SearchCriteriaTest.java
    │   │
    │   └── indexer/
    │       ├── MetadataExtractorTest.java
    │       └── PackageIndexerTest.java
    │
    ├── application/
    │   ├── CatalogServiceTest.java
    │   ├── FlowCatalogServiceTest.java
    │   └── SkillCatalogServiceTest.java
    │
    ├── infrastructure/
    │   ├── persistence/
    │   │   ├── FlowMetadataRepositoryTest.java
    │   │   └── SkillMetadataRepositoryTest.java
    │   │
    │   └── rest/
    │       ├── FlowCatalogControllerTest.java
    │       └── SkillCatalogControllerTest.java
    │
    └── integration/
        ├── CatalogIndexingIntegrationTest.java
        ├── FlowSearchIntegrationTest.java
        ├── SkillSearchIntegrationTest.java
        └── InstallationResolutionIntegrationTest.java
```

---

## Summary

**T3 Scope:** Metadata indexer and search API for discovering flows and skills through indexed release package metadata.

**Key Principles:**
1. T3 works only with release packages and registry metadata — NOT runtime state
2. Metadata is denormalized for fast search queries
3. T3 indexes are derived from T2 releases (eventually consistent)
4. Search is metadata-only (no full-text body search in Phase 0)
5. Dependencies are indexed for reverse lookup

**Implementation Order:**
1. Metadata domain models
2. DB schema
3. Package indexer
4. Metadata repository
5. Search API (list, filter, paginate)
6. Version resolution
7. Installation resolution
8. T2 integration
9. CLI commands
10. Integration tests

**Total Effort:** ~25 hours

**Integration with T2:**
- T2 calls T3 `indexRelease()` on publish
- T3 extracts metadata from `ReleasePackage`
- T3 stores in dedicated catalog tables
- Search API queries catalog tables, not releases table

---

## 17. T2 Integration Details (Based on Existing Code)

### 17.1 Event-Based Integration

T2 Registry уже использует Spring `ApplicationEventPublisher`. T3 будет слушать события:

```java
// In T3 catalog/application/
@Component
public class ReleaseEventHandler {

    private final FlowIndexer flowIndexer;
    private final SkillIndexer skillIndexer;

    @EventListener
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReleasePublished(ReleasePublishedEvent event) {
        switch (event.packageType()) {
            case FLOW -> flowIndexer.index(event.releaseId());
            case SKILL -> skillIndexer.index(event.releaseId());
        }
    }
}
```

### 17.2 Using Existing T2 Services

T3 использует существующие T2 interfaces:

| T2 Interface | Usage in T3 |
|-------------|-------------|
| `ReleaseQueryService` | Load release for indexing |
| `PackageResolver` | Resolve dependencies for indexing |
| `ReleaseRepository` | Direct access if needed |

```java
// T3 indexer uses T2 services
@Service
public class FlowIndexer {

    private final ReleaseQueryService releaseQueryService;  // T2
    private final FlowMetadataRepository flowRepository;     // T3

    public IndexingResult index(ReleaseId releaseId) {
        // 1. Load from T2
        ReleasePackage pkg = releaseQueryService.findById(releaseId)
            .orElseThrow(() -> new ReleaseNotFoundException(releaseId));

        // 2. Extract metadata
        FlowMetadata metadata = metadataExtractor.extract(pkg);

        // 3. Store in T3 tables
        flowRepository.upsert(metadata);

        return IndexingResult.success(releaseId);
    }
}
```

### 17.3 Integration Event Definition

```java
// Shared event class (in registry module)
public record ReleasePublishedEvent(
    ReleaseId releaseId,
    PackageType packageType,  // FLOW or SKILL
    Instant publishedAt
) {
    // Event published by T2 after successful release
}
```

---

## 18. PostgreSQL Full-Text Search

### 18.1 Search Vector Columns

Добавим `tsvector` columns для full-text search:

```sql
-- Add to flow_metadata
ALTER TABLE flow_metadata ADD COLUMN search_vector TSVECTOR;

-- Add to skill_metadata
ALTER TABLE skill_metadata ADD COLUMN search_vector TSVECTOR;

-- Create GIN indexes
CREATE INDEX idx_flow_search_vector ON flow_metadata USING GIN(search_vector);
CREATE INDEX idx_skill_search_vector ON skill_metadata USING GIN(search_vector);

-- Trigger to update search_vector on insert/update
CREATE OR REPLACE FUNCTION update_flow_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.display_name, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.author, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER flow_search_vector_update
    BEFORE INSERT OR UPDATE ON flow_metadata
    FOR EACH ROW EXECUTE FUNCTION update_flow_search_vector();
```

### 18.2 Search Query Implementation

```java
@Repository
public class FlowSearchRepositoryImpl implements FlowSearchRepository {

    @PersistenceContext
    private EntityManager em;

    public List<FlowMetadata> search(String query, int limit, int offset) {
        String sql = """
            SELECT f.*
            FROM flow_metadata f
            WHERE f.search_vector @@ websearch_to_tsquery('english', :query)
            ORDER BY ts_rank(f.search_vector, websearch_to_tsquery('english', :query)) DESC
            LIMIT :limit OFFSET :offset
            """;

        Query nativeQuery = em.createNativeQuery(sql, FlowMetadataEntity.class)
            .setParameter("query", query)
            .setParameter("limit", limit)
            .setParameter("offset", offset);

        return nativeQuery.getResultList();
    }
}
```

---

## 19. API Response Examples (Detailed)

### 19.1 Flow List Response

```json
{
  "flows": [
    {
      "flowId": "code-generation-flow",
      "displayName": "Code Generation Flow",
      "description": "Enterprise flow for AI-assisted code generation with review gates",
      "author": "platform-team@example.com",
      "latestVersion": "1.2.3",
      "versionCount": 5,
      "phaseCount": 4,
      "nodeCount": 15,
      "gateCount": 2,
      "tags": ["enterprise", "code-generation", "review"],
      "status": "ACTIVE",
      "firstPublishedAt": "2026-01-15T09:00:00Z",
      "lastPublishedAt": "2026-03-12T10:30:00Z"
    },
    {
      "flowId": "code-review-flow",
      "displayName": "Code Review Flow",
      "description": "Automated code review with human approval gates",
      "author": "review-team@example.com",
      "latestVersion": "2.0.0",
      "versionCount": 8,
      "phaseCount": 3,
      "nodeCount": 10,
      "gateCount": 3,
      "tags": ["review", "approval"],
      "status": "ACTIVE",
      "firstPublishedAt": "2026-02-01T14:00:00Z",
      "lastPublishedAt": "2026-03-10T11:00:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### 19.2 Skill with Dependents

```json
{
  "skillId": "code-initializer",
  "displayName": "Code Initializer",
  "description": "Initializes code structure for new projects",
  "author": "platform-team@example.com",
  "latestVersion": "2.0.0",
  "versionCount": 3,
  "skillType": "EXECUTOR",
  "handlerKind": "qwen-coder",
  "status": "ACTIVE",
  "dependents": {
    "total": 5,
    "flows": [
      {
        "flowId": "code-generation-flow",
        "displayName": "Code Generation Flow",
        "versionUsing": "1.2.3",
        "skillVersionUsed": "2.0.0"
      },
      {
        "flowId": "project-bootstrap-flow",
        "displayName": "Project Bootstrap Flow",
        "versionUsing": "0.8.0",
        "skillVersionUsed": "1.5.0"
      }
    ]
  }
}
```

### 19.3 Global Search Response

```json
{
  "query": "code gen",
  "results": [
    {
      "type": "FLOW",
      "id": "code-generation-flow",
      "displayName": "Code Generation Flow",
      "description": "Enterprise flow for AI-assisted code generation...",
      "author": "platform-team@example.com",
      "relevance": 0.95,
      "highlights": {
        "displayName": "<b>Code</b> <b>Gen</b>eration Flow"
      }
    },
    {
      "type": "SKILL",
      "id": "code-generator",
      "displayName": "Code Generator Skill",
      "description": "Generates code based on templates...",
      "author": "tools-team@example.com",
      "relevance": 0.85,
      "highlights": {
        "displayName": "<b>Code</b> <b>Gen</b>erator Skill"
      }
    }
  ],
  "total": 2,
  "pagination": {
    "page": 0,
    "size": 20,
    "hasMore": false
  }
}
```

---

## 20. Compatibility with Existing T2 Code

### 20.1 Reusing T2 Domain Models

T3 должен использовать существующие T2 models где возможно:

| T3 Model | Reuses from T2 |
|----------|---------------|
| `FlowVersionInfo.flowId()` | `ReleaseId.flowId()` |
| `FlowVersionInfo.version()` | `ReleaseVersion` |
| `FlowVersionInfo.irSha256()` | `Provenance.irChecksum()` |
| `FlowSkillDependency.skillVersion()` | `DependencyRef.version()` |

### 20.2 No Duplication Principle

**Избегать дублирования:**
- НЕ копировать `FlowIr` или `SkillIr` в T3 tables
- Хранить только searchable projections
- Ссылаться на T2 releases table по `release_id`

---

## 21. Performance Targets

| Operation | Target | Implementation |
|-----------|--------|----------------|
| Index new release | < 5s | Async event processing |
| Search flows (simple) | < 100ms | PostgreSQL indexes |
| Search flows (full-text) | < 500ms | GIN index on tsvector |
| Get version details | < 50ms | Primary key lookup |
| Resolve installation | < 100ms | Index on project_id |
| List dependents | < 200ms | Index on skill_id |

---

## 22. Monitoring & Observability

### 22.1 Metrics to Track

```java
@Component
public class CatalogMetrics {

    private final MeterRegistry meterRegistry;

    public void recordIndexingDuration(PackageType type, Duration duration) {
        Timer.builder("catalog.indexing.duration")
            .tag("type", type.name())
            .register(meterRegistry)
            .record(duration);
    }

    public void recordSearchQuery(String type, Duration duration, int results) {
        Timer.builder("catalog.search.duration")
            .tag("type", type)
            .register(meterRegistry)
            .record(duration);

        meterRegistry.counter("catalog.search.results",
            "type", type).increment(results);
    }

    public void recordIndexSize(String entityType, long count) {
        Gauge.builder("catalog.index.size", () -> count)
            .tag("entity", entityType)
            .register(meterRegistry);
    }
}
```

### 22.2 Health Check

```java
@Component
public class CatalogHealthIndicator implements HealthIndicator {

    private final FlowMetadataRepository flowRepo;
    private final SkillMetadataRepository skillRepo;

    @Override
    public Health health() {
        long flowCount = flowRepo.count();
        long skillCount = skillRepo.count();

        return Health.up()
            .withDetail("indexedFlows", flowCount)
            .withDetail("indexedSkills", skillCount)
            .build();
    }
}
```

---

## Appendix A: T3 Implementation Checklist

### Phase A: Foundation
- [ ] Create `catalog/` module structure
- [ ] Define `FlowMetadata`, `SkillMetadata` domain models
- [ ] Create `V005__catalog_tables.sql` migration
- [ ] Create JPA entities for catalog tables
- [ ] Set up Spring Data repositories

### Phase B: Indexing
- [ ] Implement `MetadataExtractor` for flows
- [ ] Implement `MetadataExtractor` for skills
- [ ] Implement `FlowIndexer` service
- [ ] Implement `SkillIndexer` service
- [ ] Create `ReleaseEventHandler` for T2 integration
- [ ] Test indexing with sample releases

### Phase C: Search API
- [ ] Implement `FlowCatalogService`
- [ ] Implement `SkillCatalogService`
- [ ] Implement `CatalogSearchService`
- [ ] Create REST controllers
- [ ] Add pagination support
- [ ] Add filtering support
- [ ] Add sorting support

### Phase D: Advanced Features
- [ ] Implement installation resolution
- [ ] Implement reverse dependency lookup
- [ ] Add PostgreSQL full-text search
- [ ] Add search result highlighting
- [ ] Add CLI commands

### Phase E: Testing & Documentation
- [ ] Unit tests for all services
- [ ] Integration tests with Testcontainers
- [ ] Performance tests for search
- [ ] API documentation (OpenAPI)
- [ ] Update project documentation
