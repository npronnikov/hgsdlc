-- liquibase formatted sql

-- changeset rule:001
CREATE TABLE rules (
    id UUID PRIMARY KEY,
    rule_id VARCHAR(255) NOT NULL,
    version VARCHAR(32) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    rule_markdown TEXT NOT NULL,
    rule_model_json JSONB,
    rule_checksum VARCHAR(128),
    saved_by VARCHAR(128) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resource_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_rules_rule_id ON rules(rule_id);
CREATE INDEX idx_rules_status ON rules(status);
