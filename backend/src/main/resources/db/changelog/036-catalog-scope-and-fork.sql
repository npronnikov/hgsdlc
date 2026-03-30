-- liquibase formatted sql

-- changeset catalog:scope-fork-001
ALTER TABLE skills ADD COLUMN scope VARCHAR(32);
ALTER TABLE skills ADD COLUMN forked_from VARCHAR(255);
ALTER TABLE skills ADD COLUMN forked_by VARCHAR(128);

-- changeset catalog:scope-fork-002
ALTER TABLE rules ADD COLUMN forked_from VARCHAR(255);
ALTER TABLE rules ADD COLUMN forked_by VARCHAR(128);

-- changeset catalog:scope-fork-003
ALTER TABLE flows ADD COLUMN scope VARCHAR(32);
ALTER TABLE flows ADD COLUMN forked_from VARCHAR(255);
ALTER TABLE flows ADD COLUMN forked_by VARCHAR(128);

-- changeset catalog:scope-fork-004
CREATE INDEX idx_skills_scope ON skills(scope);
CREATE INDEX idx_rules_scope ON rules(scope);
CREATE INDEX idx_flows_scope ON flows(scope);
