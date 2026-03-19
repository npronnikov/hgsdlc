-- liquibase formatted sql

-- changeset runtime:023-node-checkpoint
ALTER TABLE node_executions
    ADD COLUMN checkpoint_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE node_executions
    ADD COLUMN checkpoint_commit_sha VARCHAR(64);

ALTER TABLE node_executions
    ADD COLUMN checkpoint_created_at TIMESTAMP WITH TIME ZONE;
