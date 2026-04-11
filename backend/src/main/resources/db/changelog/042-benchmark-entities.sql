--liquibase formatted sql

--changeset hgd:042-benchmark-entities

CREATE TABLE benchmark_cases (
    id              UUID PRIMARY KEY,
    name            VARCHAR(512),
    instruction     TEXT NOT NULL,
    project_id      UUID NOT NULL,
    created_by      VARCHAR(128) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE benchmark_runs (
    id                  UUID PRIMARY KEY,
    case_id             UUID NOT NULL REFERENCES benchmark_cases(id),
    artifact_type       VARCHAR(16) NOT NULL,
    artifact_id         VARCHAR(255) NOT NULL,
    artifact_version_id UUID,
    coding_agent        VARCHAR(64) NOT NULL,
    run_a_id            UUID,
    run_b_id            UUID,
    status              VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    human_verdict       VARCHAR(32),
    judge_result        TEXT,
    diff_a              TEXT,
    diff_b              TEXT,
    diff_of_diffs       TEXT,
    created_by          VARCHAR(128) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at        TIMESTAMP WITH TIME ZONE,
    resource_version    BIGINT NOT NULL DEFAULT 0
);
