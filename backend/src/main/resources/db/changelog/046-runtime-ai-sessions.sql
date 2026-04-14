ALTER TABLE runs ADD COLUMN ai_session_mode VARCHAR(64) NOT NULL DEFAULT 'isolated_attempt_sessions';
ALTER TABLE runs ADD COLUMN run_session_id VARCHAR(255);
ALTER TABLE runs ADD COLUMN pending_rework_session_policy VARCHAR(64);

ALTER TABLE node_executions ADD COLUMN agent_session_id VARCHAR(255);
