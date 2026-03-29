-- liquibase formatted sql

-- changeset runtime:011
ALTER TABLE runs ADD COLUMN finalize_mode VARCHAR(16) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE runs ADD COLUMN work_branch VARCHAR(255);
ALTER TABLE runs ADD COLUMN pr_commit_strategy VARCHAR(32);
ALTER TABLE runs ADD COLUMN finalize_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE runs ADD COLUMN push_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE runs ADD COLUMN pr_status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE runs ADD COLUMN finalize_error_step VARCHAR(64);
ALTER TABLE runs ADD COLUMN final_commit_sha VARCHAR(64);
ALTER TABLE runs ADD COLUMN pr_url VARCHAR(1024);
ALTER TABLE runs ADD COLUMN pr_number INT;

UPDATE runs
SET work_branch = target_branch
WHERE work_branch IS NULL OR work_branch = '';

ALTER TABLE runs ALTER COLUMN work_branch SET NOT NULL;

UPDATE runs
SET finalize_status = 'SUCCEEDED',
    push_status = 'SKIPPED',
    pr_status = 'SKIPPED'
WHERE finalize_mode = 'LOCAL'
  AND status = 'COMPLETED';

UPDATE runs
SET finalize_status = 'SKIPPED',
    push_status = 'SKIPPED',
    pr_status = 'SKIPPED'
WHERE finalize_mode = 'LOCAL'
  AND status IN ('FAILED', 'CANCELLED');

UPDATE runs
SET pr_commit_strategy = 'SQUASH'
WHERE finalize_mode = 'PR'
  AND (pr_commit_strategy IS NULL OR pr_commit_strategy = '');
