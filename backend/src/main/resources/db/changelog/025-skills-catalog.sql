-- liquibase formatted sql

-- changeset skill:003
ALTER TABLE skills ADD COLUMN team_code VARCHAR(128);
ALTER TABLE skills ADD COLUMN platform_code VARCHAR(32);
ALTER TABLE skills ADD COLUMN tags_json TEXT;
ALTER TABLE skills ADD COLUMN skill_kind VARCHAR(64);
ALTER TABLE skills ADD COLUMN environment VARCHAR(16);
ALTER TABLE skills ADD COLUMN approval_status VARCHAR(32);
ALTER TABLE skills ADD COLUMN approved_by VARCHAR(128);
ALTER TABLE skills ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE skills ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE skills ADD COLUMN source_ref VARCHAR(128);
ALTER TABLE skills ADD COLUMN source_path VARCHAR(512);
ALTER TABLE skills ADD COLUMN content_source VARCHAR(16);
ALTER TABLE skills ADD COLUMN visibility VARCHAR(32);
ALTER TABLE skills ADD COLUMN lifecycle_status VARCHAR(32);

UPDATE skills
SET approval_status = CASE
    WHEN status = 'PUBLISHED' THEN 'PUBLISHED'
    ELSE 'DRAFT'
END
WHERE approval_status IS NULL;

UPDATE skills
SET content_source = 'DB'
WHERE content_source IS NULL;

UPDATE skills
SET environment = 'DEV'
WHERE environment IS NULL;

UPDATE skills
SET visibility = 'INTERNAL'
WHERE visibility IS NULL;

UPDATE skills
SET lifecycle_status = 'ACTIVE'
WHERE lifecycle_status IS NULL;

UPDATE skills
SET tags_json = '[]'
WHERE tags_json IS NULL;

-- changeset skill:004
CREATE TABLE tags (
    code VARCHAR(128) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
