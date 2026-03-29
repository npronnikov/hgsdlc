-- liquibase formatted sql

-- changeset runtime:012
ALTER TABLE runs ADD COLUMN publish_mode VARCHAR(16) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE runs ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE runs ADD COLUMN publish_error_step VARCHAR(64);
ALTER TABLE runs ADD COLUMN publish_commit_sha VARCHAR(64);

UPDATE runs
SET publish_mode = finalize_mode
WHERE finalize_mode IS NOT NULL;

UPDATE runs
SET publish_status = finalize_status
WHERE finalize_status IS NOT NULL;

UPDATE runs
SET publish_error_step = finalize_error_step
WHERE finalize_error_step IS NOT NULL;

UPDATE runs
SET publish_commit_sha = final_commit_sha
WHERE final_commit_sha IS NOT NULL;

UPDATE runs
SET status = 'WAITING_PUBLISH'
WHERE status = 'WAITING_FINALIZE';

UPDATE runs
SET status = 'PUBLISH_FAILED'
WHERE status = 'FINALIZE_FAILED';
