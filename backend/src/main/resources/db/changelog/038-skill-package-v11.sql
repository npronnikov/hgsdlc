-- liquibase formatted sql

-- changeset skill:006
CREATE TABLE skill_files (
    id UUID PRIMARY KEY,
    skill_version_id UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    path VARCHAR(1024) NOT NULL,
    role VARCHAR(32) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    is_executable BOOLEAN NOT NULL DEFAULT FALSE,
    text_content TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE skill_files
    ADD CONSTRAINT uq_skill_files_skill_version_path UNIQUE (skill_version_id, path);
ALTER TABLE skill_files
    ADD CONSTRAINT ck_skill_files_role CHECK (role IN ('instruction', 'script', 'template', 'asset'));
ALTER TABLE skill_files
    ADD CONSTRAINT ck_skill_files_path_relative CHECK (
        path NOT LIKE '/%' AND path NOT LIKE '%\\%' AND path NOT LIKE '%..%' AND path NOT LIKE '.git%' AND path NOT LIKE '.svn%'
    );
ALTER TABLE skill_files
    ADD CONSTRAINT ck_skill_files_exec_scope CHECK (is_executable = FALSE OR path LIKE 'scripts/%');

CREATE INDEX idx_skill_files_skill_version_role ON skill_files(skill_version_id, role);

INSERT INTO skill_files (
    id,
    skill_version_id,
    path,
    role,
    media_type,
    is_executable,
    text_content,
    size_bytes,
    created_at,
    updated_at
)
SELECT
    s.id,
    s.id,
    'SKILL.md',
    'instruction',
    'text/markdown',
    FALSE,
    s.skill_markdown,
    CHAR_LENGTH(s.skill_markdown),
    COALESCE(s.saved_at, NOW()),
    COALESCE(s.saved_at, NOW())
FROM skills s
WHERE NOT EXISTS (
    SELECT 1
    FROM skill_files sf
    WHERE sf.skill_version_id = s.id
);
