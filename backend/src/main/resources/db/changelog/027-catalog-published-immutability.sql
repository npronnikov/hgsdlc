-- liquibase formatted sql

-- changeset catalog:immutability-001 dbms:postgresql splitStatements:false
CREATE OR REPLACE FUNCTION forbid_published_catalog_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    status_value TEXT;
    approval_status_value TEXT;
BEGIN
    status_value := upper(coalesce(to_jsonb(OLD)->>'status', ''));
    approval_status_value := upper(coalesce(to_jsonb(OLD)->>'approval_status', ''));

    IF status_value = 'PUBLISHED' OR approval_status_value = 'PUBLISHED' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Cannot delete published % (canonical_name=%)', TG_TABLE_NAME, to_jsonb(OLD)->>'canonical_name';
        END IF;

        IF NEW IS DISTINCT FROM OLD THEN
            RAISE EXCEPTION 'Cannot update published % (canonical_name=%)', TG_TABLE_NAME, to_jsonb(OLD)->>'canonical_name';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

-- changeset catalog:immutability-002 dbms:postgresql
DROP TRIGGER IF EXISTS trg_rules_forbid_published_mutation ON rules;
CREATE TRIGGER trg_rules_forbid_published_mutation
BEFORE UPDATE OR DELETE ON rules
FOR EACH ROW
EXECUTE FUNCTION forbid_published_catalog_mutation();

-- changeset catalog:immutability-003 dbms:postgresql
DROP TRIGGER IF EXISTS trg_skills_forbid_published_mutation ON skills;
CREATE TRIGGER trg_skills_forbid_published_mutation
BEFORE UPDATE OR DELETE ON skills
FOR EACH ROW
EXECUTE FUNCTION forbid_published_catalog_mutation();

-- changeset catalog:immutability-004 dbms:postgresql
DROP TRIGGER IF EXISTS trg_flows_forbid_published_mutation ON flows;
CREATE TRIGGER trg_flows_forbid_published_mutation
BEFORE UPDATE OR DELETE ON flows
FOR EACH ROW
EXECUTE FUNCTION forbid_published_catalog_mutation();
