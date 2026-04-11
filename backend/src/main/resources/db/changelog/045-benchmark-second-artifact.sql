--liquibase formatted sql

--changeset hgd:045-benchmark-second-artifact
ALTER TABLE benchmark_cases ADD COLUMN artifact_b_type VARCHAR(16);
ALTER TABLE benchmark_cases ADD COLUMN artifact_b_id   VARCHAR(255);

ALTER TABLE benchmark_runs ADD COLUMN artifact_b_type       VARCHAR(16);
ALTER TABLE benchmark_runs ADD COLUMN artifact_b_id         VARCHAR(255);
ALTER TABLE benchmark_runs ADD COLUMN artifact_b_version_id UUID;
