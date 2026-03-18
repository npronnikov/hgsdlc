-- liquibase formatted sql

-- changeset runtime:019-flow-coding-agent
ALTER TABLE flows ADD COLUMN coding_agent VARCHAR(64);

UPDATE flows
SET coding_agent = CASE
    WHEN flow_yaml LIKE '%coding_agent: claude%' THEN 'claude'
    WHEN flow_yaml LIKE '%coding_agent: cursor%' THEN 'cursor'
    WHEN flow_yaml LIKE '%coding_agent: qwen%' THEN 'qwen'
    ELSE 'qwen'
END;

UPDATE flows
SET flow_yaml = REPLACE(
    REPLACE(
        REPLACE(flow_yaml, 'coding_agent: qwen
', ''),
        'coding_agent: claude
', ''
    ),
        'coding_agent: cursor
', ''
);

ALTER TABLE flows ALTER COLUMN coding_agent SET NOT NULL;
