-- liquibase formatted sql

-- changeset coding-agent:001
ALTER TABLE rules RENAME COLUMN provider TO coding_agent;
ALTER TABLE skills RENAME COLUMN provider TO coding_agent;
