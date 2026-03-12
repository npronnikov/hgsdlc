-- Registry tables for T2: Release registry, artifacts, dependencies, and lockfile snapshots

-- Release registry
-- Stores metadata for published flow and skill releases
CREATE TABLE releases (
    id BIGSERIAL PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    version VARCHAR(64) NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    author VARCHAR(255),
    git_commit VARCHAR(40) NOT NULL,
    git_tag VARCHAR(255),
    package_hash VARCHAR(64) NOT NULL,
    provenance_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(flow_id, version)
);

-- Index for querying releases by flow_id
CREATE INDEX idx_releases_flow_id ON releases(flow_id);
-- Index for querying releases by version
CREATE INDEX idx_releases_version ON releases(version);

-- Release artifacts
-- Stores individual artifact files within a release package
CREATE TABLE release_artifacts (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES releases(id) ON DELETE CASCADE,
    artifact_type VARCHAR(32) NOT NULL,  -- 'flow_ir', 'phase_ir', 'skill_ir', 'manifest'
    artifact_key VARCHAR(255) NOT NULL,  -- phase_id or skill_id
    content_hash VARCHAR(64) NOT NULL,
    content JSONB NOT NULL,
    UNIQUE(release_id, artifact_type, artifact_key)
);

-- Index for querying artifacts by release
CREATE INDEX idx_artifacts_release ON release_artifacts(release_id);

-- Release dependencies
-- Stores flow -> skill dependencies for a release
CREATE TABLE release_dependencies (
    id BIGSERIAL PRIMARY KEY,
    release_id BIGINT NOT NULL REFERENCES releases(id) ON DELETE CASCADE,
    skill_flow_id VARCHAR(255) NOT NULL,
    skill_version VARCHAR(64) NOT NULL,
    UNIQUE(release_id, skill_flow_id, skill_version)
);

-- Index for querying dependencies by release
CREATE INDEX idx_deps_release ON release_dependencies(release_id);

-- Lockfile snapshots
-- Cached lockfiles for deterministic install baselines
CREATE TABLE lockfile_snapshots (
    id BIGSERIAL PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    flow_version VARCHAR(64) NOT NULL,
    lockfile_hash VARCHAR(64) NOT NULL,
    lockfile_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(flow_id, flow_version, lockfile_hash)
);
