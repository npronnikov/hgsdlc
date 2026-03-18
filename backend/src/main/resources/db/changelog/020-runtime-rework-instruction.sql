-- liquibase formatted sql

-- changeset runtime:020-rework-instruction
ALTER TABLE runs ADD COLUMN pending_rework_instruction TEXT;
