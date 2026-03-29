-- liquibase formatted sql

-- changeset runtime:013
ALTER TABLE runs DROP COLUMN finalize_mode;
ALTER TABLE runs DROP COLUMN finalize_status;
ALTER TABLE runs DROP COLUMN finalize_error_step;
ALTER TABLE runs DROP COLUMN final_commit_sha;
