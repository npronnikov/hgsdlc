-- liquibase formatted sql

-- changeset seed:content-source-db-001
UPDATE rules
SET content_source = 'DB',
    source_ref = NULL,
    source_path = NULL
WHERE saved_by = 'seed';

-- changeset seed:content-source-db-002
UPDATE skills
SET content_source = 'DB',
    source_ref = NULL,
    source_path = NULL
WHERE saved_by = 'seed';

-- changeset seed:content-source-db-003
UPDATE flows
SET content_source = 'DB',
    source_ref = NULL,
    source_path = NULL
WHERE saved_by = 'seed';

