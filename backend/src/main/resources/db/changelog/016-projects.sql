-- liquibase formatted sql

-- changeset project:001
CREATE TABLE projects (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    repo_url VARCHAR(512) NOT NULL,
    default_branch VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_run_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resource_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_name ON projects(name);
