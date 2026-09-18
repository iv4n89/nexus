CREATE TABLE backup_policies (
    project_id VARCHAR(64) PRIMARY KEY,
    daily_retention INTEGER NOT NULL,
    weekly_retention INTEGER NOT NULL,
    monthly_retention INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    schedule_cron VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE backups (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    artifact_uri TEXT,
    summary TEXT,
    includes_db BOOLEAN NOT NULL DEFAULT FALSE,
    includes_volumes BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_backups_project ON backups (project_id, created_at DESC);
