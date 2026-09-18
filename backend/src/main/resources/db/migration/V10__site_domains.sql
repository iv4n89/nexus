CREATE TABLE site_domains (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id),
    hostname VARCHAR(253) NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    target_port INTEGER NOT NULL,
    cert_status VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_site_domains_hostname ON site_domains (hostname);
CREATE INDEX idx_site_domains_project ON site_domains (project_id, created_at DESC);
