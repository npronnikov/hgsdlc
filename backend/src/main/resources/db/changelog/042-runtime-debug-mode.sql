--liquibase formatted sql

--changeset hgd:042-runtime-debug-mode
ALTER TABLE runs ADD COLUMN debug_mode BOOLEAN NOT NULL DEFAULT FALSE;
