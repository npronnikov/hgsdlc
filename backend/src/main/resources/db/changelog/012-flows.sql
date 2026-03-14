-- liquibase formatted sql

-- changeset flow:001
CREATE TABLE flows (
    id UUID PRIMARY KEY,
    flow_id VARCHAR(255) NOT NULL,
    version VARCHAR(32) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    start_role VARCHAR(255) NOT NULL,
    approver_role VARCHAR(255) NOT NULL,
    start_node_id VARCHAR(255) NOT NULL,
    rule_refs TEXT,
    flow_yaml TEXT NOT NULL,
    checksum VARCHAR(128),
    saved_by VARCHAR(128) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resource_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_flows_flow_id ON flows(flow_id);
CREATE INDEX idx_flows_status ON flows(status);
