-- liquibase formatted sql

-- changeset rule:003
ALTER TABLE rules ADD COLUMN description VARCHAR(1024);
