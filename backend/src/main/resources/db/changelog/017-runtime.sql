-- liquibase formatted sql

-- changeset runtime:001
CREATE TABLE runs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    target_branch VARCHAR(255) NOT NULL,
    flow_canonical_name VARCHAR(255) NOT NULL,
    flow_snapshot_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_node_id VARCHAR(255) NOT NULL,
    feature_request TEXT NOT NULL,
    context_root_dir VARCHAR(1024),
    context_file_manifest_json TEXT,
    workspace_root VARCHAR(2048),
    error_code VARCHAR(128),
    error_message TEXT,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    resource_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_runs_project_branch_status ON runs(project_id, target_branch, status);
CREATE INDEX idx_runs_status ON runs(status);
CREATE INDEX idx_runs_created_at ON runs(created_at);

-- changeset runtime:002
CREATE TABLE node_executions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    node_id VARCHAR(255) NOT NULL,
    node_kind VARCHAR(64) NOT NULL,
    attempt_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(128),
    error_message TEXT,
    resource_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_node_executions_run ON node_executions(run_id, started_at);
CREATE INDEX idx_node_executions_run_node ON node_executions(run_id, node_id, attempt_no);

-- changeset runtime:003
CREATE TABLE gate_instances (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    node_execution_id UUID NOT NULL,
    node_id VARCHAR(255) NOT NULL,
    gate_kind VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    assignee_role VARCHAR(64),
    payload_json TEXT,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    resource_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_gate_instances_run ON gate_instances(run_id, opened_at);
CREATE INDEX idx_gate_instances_status_role ON gate_instances(status, assignee_role);

-- changeset runtime:004
CREATE TABLE artifact_versions (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    node_id VARCHAR(255) NOT NULL,
    artifact_key VARCHAR(255) NOT NULL,
    path VARCHAR(2048) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    checksum VARCHAR(128),
    size_bytes BIGINT,
    supersedes_artifact_version_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_artifact_versions_run ON artifact_versions(run_id, created_at);
CREATE INDEX idx_artifact_versions_run_key ON artifact_versions(run_id, artifact_key, created_at);

-- changeset runtime:005
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    node_execution_id UUID,
    gate_id UUID,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(128),
    payload_json TEXT
);

CREATE UNIQUE INDEX uq_audit_events_run_seq ON audit_events(run_id, sequence_no);
CREATE INDEX idx_audit_events_run_time ON audit_events(run_id, event_time);
