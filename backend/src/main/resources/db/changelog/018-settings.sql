-- liquibase formatted sql

-- changeset settings:001
CREATE TABLE system_settings (
    setting_key VARCHAR(128) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(128)
);
