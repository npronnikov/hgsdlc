-- liquibase formatted sql

-- changeset publication:005
ALTER TABLE rules ADD COLUMN publication_status VARCHAR(32);
ALTER TABLE rules ADD COLUMN publication_target VARCHAR(32);
ALTER TABLE rules ADD COLUMN published_commit_sha VARCHAR(64);
ALTER TABLE rules ADD COLUMN published_pr_url VARCHAR(1024);
ALTER TABLE rules ADD COLUMN last_publish_error TEXT;

UPDATE rules
SET publication_status = CASE
    WHEN approval_status = 'PUBLISHED' OR status = 'PUBLISHED' THEN 'PUBLISHED'
    WHEN approval_status = 'PENDING_APPROVAL' THEN 'PENDING_APPROVAL'
    ELSE 'DRAFT'
END
WHERE publication_status IS NULL;

UPDATE rules
SET publication_target = CASE
    WHEN content_source = 'GIT' THEN 'GIT_ONLY'
    ELSE 'DB_ONLY'
END
WHERE publication_target IS NULL;

CREATE INDEX idx_rules_publication_status ON rules(publication_status);

-- changeset publication:006
ALTER TABLE flows ADD COLUMN publication_status VARCHAR(32);
ALTER TABLE flows ADD COLUMN publication_target VARCHAR(32);
ALTER TABLE flows ADD COLUMN published_commit_sha VARCHAR(64);
ALTER TABLE flows ADD COLUMN published_pr_url VARCHAR(1024);
ALTER TABLE flows ADD COLUMN last_publish_error TEXT;

UPDATE flows
SET publication_status = CASE
    WHEN approval_status = 'PUBLISHED' OR status = 'PUBLISHED' THEN 'PUBLISHED'
    WHEN approval_status = 'PENDING_APPROVAL' THEN 'PENDING_APPROVAL'
    ELSE 'DRAFT'
END
WHERE publication_status IS NULL;

UPDATE flows
SET publication_target = CASE
    WHEN content_source = 'GIT' THEN 'GIT_ONLY'
    ELSE 'DB_ONLY'
END
WHERE publication_target IS NULL;

CREATE INDEX idx_flows_publication_status ON flows(publication_status);
