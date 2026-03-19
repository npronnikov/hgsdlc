-- liquibase formatted sql

-- changeset audit-indexes:001
CREATE INDEX IF NOT EXISTS idx_audit_events_run_node_time ON audit_events(run_id, node_execution_id, event_time DESC);

-- changeset audit-indexes:002
CREATE INDEX IF NOT EXISTS idx_audit_events_run_type_time ON audit_events(run_id, event_type, event_time DESC);

-- changeset audit-indexes:003
CREATE INDEX IF NOT EXISTS idx_audit_events_run_actor_time ON audit_events(run_id, actor_type, event_time DESC);
