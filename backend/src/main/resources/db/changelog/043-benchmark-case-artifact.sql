--liquibase formatted sql

--changeset hgd:043-benchmark-case-artifact
ALTER TABLE benchmark_cases ADD COLUMN artifact_type VARCHAR(16);
ALTER TABLE benchmark_cases ADD COLUMN artifact_id  VARCHAR(255);
