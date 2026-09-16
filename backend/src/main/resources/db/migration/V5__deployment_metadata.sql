ALTER TABLE deployments ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
