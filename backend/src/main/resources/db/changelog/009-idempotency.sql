-- liquibase formatted sql

-- changeset idempotency:001
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    scope VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_json JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (idempotency_key, scope)
);

CREATE INDEX idx_idempotency_created_at ON idempotency_keys(created_at);
