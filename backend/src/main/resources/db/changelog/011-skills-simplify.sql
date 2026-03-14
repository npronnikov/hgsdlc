-- liquibase formatted sql

-- changeset skill:002
ALTER TABLE skills ADD COLUMN name VARCHAR(255);
ALTER TABLE skills ADD COLUMN description VARCHAR(512);
ALTER TABLE skills ADD COLUMN provider VARCHAR(64);
ALTER TABLE skills ADD COLUMN checksum VARCHAR(128);

UPDATE skills SET checksum = skill_checksum WHERE checksum IS NULL;

ALTER TABLE skills DROP COLUMN skill_checksum;
ALTER TABLE skills DROP COLUMN skill_model_json;
