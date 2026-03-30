ALTER TABLE publication_requests DROP COLUMN IF EXISTS requested_target;

ALTER TABLE skills DROP COLUMN IF EXISTS environment;
ALTER TABLE skills DROP COLUMN IF EXISTS content_source;
ALTER TABLE skills DROP COLUMN IF EXISTS visibility;
ALTER TABLE skills DROP COLUMN IF EXISTS publication_target;

ALTER TABLE rules DROP COLUMN IF EXISTS environment;
ALTER TABLE rules DROP COLUMN IF EXISTS content_source;
ALTER TABLE rules DROP COLUMN IF EXISTS visibility;
ALTER TABLE rules DROP COLUMN IF EXISTS publication_target;

ALTER TABLE flows DROP COLUMN IF EXISTS environment;
ALTER TABLE flows DROP COLUMN IF EXISTS content_source;
ALTER TABLE flows DROP COLUMN IF EXISTS visibility;
ALTER TABLE flows DROP COLUMN IF EXISTS publication_target;
