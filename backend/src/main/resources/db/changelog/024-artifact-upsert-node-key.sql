-- liquibase formatted sql

-- changeset runtime:024-artifact-upsert-node-key
ALTER TABLE artifact_versions DROP CONSTRAINT IF EXISTS uq_artifact_versions_run_key;

ALTER TABLE artifact_versions
    ADD CONSTRAINT uq_artifact_versions_run_node_key UNIQUE (run_id, node_id, artifact_key);

CREATE INDEX IF NOT EXISTS idx_artifact_versions_run_key ON artifact_versions(run_id, artifact_key, created_at);
