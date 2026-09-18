CREATE TABLE security_findings (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    source VARCHAR(64) NOT NULL,
    package_name VARCHAR(256),
    installed_version VARCHAR(128),
    fixed_version VARCHAR(128),
    title TEXT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    first_seen TIMESTAMPTZ NOT NULL,
    last_seen TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_security_findings_project_fingerprint
    ON security_findings (project_id, fingerprint);
CREATE INDEX idx_security_findings_project_last_seen
    ON security_findings (project_id, last_seen DESC);
