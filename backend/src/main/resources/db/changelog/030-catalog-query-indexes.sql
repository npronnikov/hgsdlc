-- liquibase formatted sql

-- changeset catalog-indexes:001
CREATE INDEX IF NOT EXISTS idx_skills_saved_at_id
    ON skills(saved_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_skills_skill_id_status_saved_at
    ON skills(skill_id, status, saved_at DESC, id DESC);

-- changeset catalog-indexes:002
CREATE INDEX IF NOT EXISTS idx_rules_saved_at_id
    ON rules(saved_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_rules_rule_id_status_saved_at
    ON rules(rule_id, status, saved_at DESC, id DESC);

-- changeset catalog-indexes:003
CREATE INDEX IF NOT EXISTS idx_flows_saved_at_id
    ON flows(saved_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_flows_flow_id_status_saved_at
    ON flows(flow_id, status, saved_at DESC, id DESC);
