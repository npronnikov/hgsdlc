-- liquibase formatted sql

-- changeset init:1
-- comment: Initial schema for Human-Guided SDLC Platform

-- Flow registry
CREATE TABLE flows (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    ir BYTEA NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    UNIQUE(name, version)
);

-- Skill registry
CREATE TABLE skills (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    handler VARCHAR(500) NOT NULL,
    input_schema JSONB,
    output_schema JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    UNIQUE(name, version)
);

-- Run instances
CREATE TABLE runs (
    id UUID PRIMARY KEY,
    flow_id UUID NOT NULL REFERENCES flows(id),
    flow_version VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    current_stage VARCHAR(100),
    context JSONB,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL
);

-- Run stages
CREATE TABLE run_stages (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES runs(id),
    stage_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(run_id, stage_id)
);

-- Gates
CREATE TABLE gates (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES runs(id),
    stage_id VARCHAR(100) NOT NULL,
    gate_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    required_approvers INTEGER DEFAULT 1,
    approved_by JSONB,
    rejected_by JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- Evidence (CAS references)
CREATE TABLE evidence (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES runs(id),
    node_id VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(100),
    storage_ref VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Audit log
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_runs_flow_id ON runs(flow_id);
CREATE INDEX idx_runs_status ON runs(status);
CREATE INDEX idx_run_stages_run_id ON run_stages(run_id);
CREATE INDEX idx_gates_run_id ON gates(run_id);
CREATE INDEX idx_evidence_run_id ON evidence(run_id);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);

-- changeset init:2
-- comment: Add Liquibase tables

-- Liquibase will create its own tables automatically
