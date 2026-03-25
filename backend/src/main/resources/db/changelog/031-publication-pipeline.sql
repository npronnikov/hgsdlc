-- liquibase formatted sql

-- changeset publication:001
CREATE TABLE publication_requests (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    version VARCHAR(64) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    author VARCHAR(128) NOT NULL,
    requested_target VARCHAR(32) NOT NULL,
    requested_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    approval_count INT NOT NULL DEFAULT 0,
    required_approvals INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error TEXT
);

CREATE UNIQUE INDEX uk_publication_requests_entity
    ON publication_requests(entity_type, entity_id, version);
CREATE INDEX idx_publication_requests_status
    ON publication_requests(status, created_at DESC);

-- changeset publication:002
CREATE TABLE publication_approvals (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES publication_requests(id) ON DELETE CASCADE,
    approver VARCHAR(128) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_publication_approvals_request
    ON publication_approvals(request_id, created_at DESC);

-- changeset publication:003
CREATE TABLE publication_jobs (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES publication_requests(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    step VARCHAR(64),
    attempt_no INT NOT NULL DEFAULT 1,
    branch_name VARCHAR(255),
    pr_url VARCHAR(1024),
    pr_number INT,
    commit_sha VARCHAR(64),
    log_excerpt TEXT,
    error TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_publication_jobs_status
    ON publication_jobs(status, created_at DESC);
CREATE INDEX idx_publication_jobs_request
    ON publication_jobs(request_id, created_at DESC);

-- changeset publication:004
ALTER TABLE skills ADD COLUMN publication_status VARCHAR(32);
ALTER TABLE skills ADD COLUMN publication_target VARCHAR(32);
ALTER TABLE skills ADD COLUMN published_commit_sha VARCHAR(64);
ALTER TABLE skills ADD COLUMN published_pr_url VARCHAR(1024);
ALTER TABLE skills ADD COLUMN last_publish_error TEXT;

UPDATE skills
SET publication_status = CASE
    WHEN approval_status = 'PUBLISHED' OR status = 'PUBLISHED' THEN 'PUBLISHED'
    WHEN approval_status = 'PENDING_APPROVAL' THEN 'PENDING_APPROVAL'
    ELSE 'DRAFT'
END
WHERE publication_status IS NULL;

UPDATE skills
SET publication_target = CASE
    WHEN content_source = 'GIT' THEN 'GIT_ONLY'
    ELSE 'DB_ONLY'
END
WHERE publication_target IS NULL;

CREATE INDEX idx_skills_publication_status ON skills(publication_status);
