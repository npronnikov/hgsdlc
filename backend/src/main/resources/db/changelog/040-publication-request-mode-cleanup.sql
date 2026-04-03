-- liquibase formatted sql

-- changeset publication:005
ALTER TABLE publication_requests DROP COLUMN IF EXISTS requested_mode;
