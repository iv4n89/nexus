ALTER TABLE traffic_hourly
    ADD COLUMN IF NOT EXISTS top_endpoints JSONB NOT NULL DEFAULT '[]'::jsonb;
