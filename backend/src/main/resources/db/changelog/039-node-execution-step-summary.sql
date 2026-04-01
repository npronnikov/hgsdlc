-- liquibase formatted sql

-- changeset runtime:039
ALTER TABLE node_executions ADD COLUMN step_summary_json TEXT;
