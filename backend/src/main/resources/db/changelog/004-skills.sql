-- liquibase formatted sql

-- changeset skill:001
CREATE TABLE skills (
    id UUID PRIMARY KEY,
    skill_id VARCHAR(255) NOT NULL,
    version VARCHAR(32) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    skill_markdown TEXT NOT NULL,
    skill_model_json JSONB,
    skill_checksum VARCHAR(128),
    saved_by VARCHAR(128) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resource_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_skills_skill_id ON skills(skill_id);
CREATE INDEX idx_skills_status ON skills(status);
