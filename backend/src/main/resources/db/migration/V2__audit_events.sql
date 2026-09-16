CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    action VARCHAR(32) NOT NULL,
    project_id VARCHAR(64),
    service_id VARCHAR(64),
    ip VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_created_at ON audit_events (created_at DESC);
