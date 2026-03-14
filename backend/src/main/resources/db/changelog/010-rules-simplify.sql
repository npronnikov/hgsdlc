-- liquibase formatted sql

-- changeset rule:002
ALTER TABLE rules ADD COLUMN title VARCHAR(255);
ALTER TABLE rules ADD COLUMN provider VARCHAR(64);
ALTER TABLE rules ADD COLUMN checksum VARCHAR(128);

UPDATE rules SET checksum = rule_checksum WHERE checksum IS NULL;

ALTER TABLE rules DROP COLUMN rule_checksum;
ALTER TABLE rules DROP COLUMN rule_model_json;
