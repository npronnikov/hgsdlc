--liquibase formatted sql

--changeset hgd:044-benchmark-run-comments
ALTER TABLE benchmark_runs ADD COLUMN review_comment TEXT;
ALTER TABLE benchmark_runs ADD COLUMN line_comments_json TEXT;
