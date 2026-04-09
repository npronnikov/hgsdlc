--liquibase formatted sql

--changeset hgd:041-runtime-skip-gates
ALTER TABLE runs ADD COLUMN skip_gates BOOLEAN NOT NULL DEFAULT FALSE;
