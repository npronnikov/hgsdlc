-- liquibase formatted sql

-- changeset embedding:add-pgvector-extension
CREATE EXTENSION IF NOT EXISTS vector;

-- changeset embedding:add-skills-embedding-column
ALTER TABLE skills ADD COLUMN embedding_vector vector(384);

-- changeset embedding:add-rules-embedding-column
ALTER TABLE rules ADD COLUMN embedding_vector vector(384);

-- changeset embedding:create-skills-embedding-index
CREATE INDEX idx_skills_embedding_vector ON skills USING ivfflat (embedding_vector vector_cosine_ops) WITH (lists = 100);

-- changeset embedding:create-rules-embedding-index
CREATE INDEX idx_rules_embedding_vector ON rules USING ivfflat (embedding_vector vector_cosine_ops) WITH (lists = 100);
