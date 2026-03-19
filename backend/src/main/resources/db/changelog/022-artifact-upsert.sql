-- liquibase formatted sql

-- changeset runtime:022-artifact-upsert
ALTER TABLE artifact_versions ADD COLUMN version_no INT NOT NULL DEFAULT 1;

DELETE FROM artifact_versions a
WHERE a.id NOT IN (
    SELECT sub.id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY run_id, artifact_key ORDER BY created_at DESC) AS rn
        FROM artifact_versions
    ) sub
    WHERE sub.rn = 1
);

ALTER TABLE artifact_versions DROP COLUMN supersedes_artifact_version_id;

ALTER TABLE artifact_versions ADD CONSTRAINT uq_artifact_versions_run_key UNIQUE (run_id, artifact_key);

DROP INDEX IF EXISTS idx_artifact_versions_run_key;
