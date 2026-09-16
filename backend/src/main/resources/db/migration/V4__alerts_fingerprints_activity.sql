CREATE TABLE log_error_fingerprints (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(64) NOT NULL DEFAULT '',
    fingerprint VARCHAR(64) NOT NULL,
    first_seen TIMESTAMPTZ NOT NULL,
    last_seen TIMESTAMPTZ NOT NULL,
    count BIGINT NOT NULL,
    sample_message TEXT NOT NULL
);
CREATE UNIQUE INDEX uq_fingerprint ON log_error_fingerprints (project_id, service_id, fingerprint);

CREATE TABLE alert_rules (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64),
    type VARCHAR(32) NOT NULL,
    threshold_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE alert_events (
    id UUID PRIMARY KEY,
    rule_id UUID REFERENCES alert_rules(id),
    project_id VARCHAR(64),
    service_id VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_alert_events_status ON alert_events (status, opened_at DESC);

CREATE TABLE activity_events (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    type VARCHAR(32) NOT NULL,
    project_id VARCHAR(64),
    service_id VARCHAR(64),
    message TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_activity_created_at ON activity_events (created_at DESC);
