-- liquibase formatted sql

-- changeset flow:002
ALTER TABLE flows ALTER COLUMN start_role DROP NOT NULL;
ALTER TABLE flows ALTER COLUMN approver_role DROP NOT NULL;
