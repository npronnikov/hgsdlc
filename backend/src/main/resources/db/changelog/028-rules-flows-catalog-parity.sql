-- liquibase formatted sql

-- changeset rule:catalog-parity-001
ALTER TABLE rules ADD COLUMN team_code VARCHAR(128);
ALTER TABLE rules ADD COLUMN platform_code VARCHAR(32);
ALTER TABLE rules ADD COLUMN tags_json TEXT;
ALTER TABLE rules ADD COLUMN rule_kind VARCHAR(64);
ALTER TABLE rules ADD COLUMN scope VARCHAR(32);
ALTER TABLE rules ADD COLUMN environment VARCHAR(16);
ALTER TABLE rules ADD COLUMN approval_status VARCHAR(32);
ALTER TABLE rules ADD COLUMN approved_by VARCHAR(128);
ALTER TABLE rules ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE rules ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE rules ADD COLUMN source_ref VARCHAR(128);
ALTER TABLE rules ADD COLUMN source_path VARCHAR(512);
ALTER TABLE rules ADD COLUMN content_source VARCHAR(16);
ALTER TABLE rules ADD COLUMN visibility VARCHAR(32);
ALTER TABLE rules ADD COLUMN lifecycle_status VARCHAR(32);

UPDATE rules
SET approval_status = CASE
    WHEN status = 'PUBLISHED' THEN 'PUBLISHED'
    ELSE 'DRAFT'
END
WHERE approval_status IS NULL;

UPDATE rules SET content_source = 'DB' WHERE content_source IS NULL;
UPDATE rules SET environment = 'DEV' WHERE environment IS NULL;
UPDATE rules SET visibility = 'INTERNAL' WHERE visibility IS NULL;
UPDATE rules SET lifecycle_status = 'ACTIVE' WHERE lifecycle_status IS NULL;
UPDATE rules SET tags_json = '[]' WHERE tags_json IS NULL;

-- changeset flow:catalog-parity-001
ALTER TABLE flows ADD COLUMN team_code VARCHAR(128);
ALTER TABLE flows ADD COLUMN platform_code VARCHAR(32);
ALTER TABLE flows ADD COLUMN tags_json TEXT;
ALTER TABLE flows ADD COLUMN flow_kind VARCHAR(64);
ALTER TABLE flows ADD COLUMN risk_level VARCHAR(32);
ALTER TABLE flows ADD COLUMN environment VARCHAR(16);
ALTER TABLE flows ADD COLUMN approval_status VARCHAR(32);
ALTER TABLE flows ADD COLUMN approved_by VARCHAR(128);
ALTER TABLE flows ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE flows ADD COLUMN published_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE flows ADD COLUMN source_ref VARCHAR(128);
ALTER TABLE flows ADD COLUMN source_path VARCHAR(512);
ALTER TABLE flows ADD COLUMN content_source VARCHAR(16);
ALTER TABLE flows ADD COLUMN visibility VARCHAR(32);
ALTER TABLE flows ADD COLUMN lifecycle_status VARCHAR(32);

UPDATE flows
SET approval_status = CASE
    WHEN status = 'PUBLISHED' THEN 'PUBLISHED'
    ELSE 'DRAFT'
END
WHERE approval_status IS NULL;

UPDATE flows SET content_source = 'DB' WHERE content_source IS NULL;
UPDATE flows SET environment = 'DEV' WHERE environment IS NULL;
UPDATE flows SET visibility = 'INTERNAL' WHERE visibility IS NULL;
UPDATE flows SET lifecycle_status = 'ACTIVE' WHERE lifecycle_status IS NULL;
UPDATE flows SET tags_json = '[]' WHERE tags_json IS NULL;
