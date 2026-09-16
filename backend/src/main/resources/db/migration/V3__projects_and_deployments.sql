CREATE TABLE projects (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    working_directory TEXT NOT NULL,
    manifest_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deployments (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id),
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    triggered_by VARCHAR(64) NOT NULL,
    commit_sha VARCHAR(64),
    exit_code INTEGER,
    output_summary TEXT,
    health_ok BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_deployments_project ON deployments (project_id, created_at DESC);

CREATE UNIQUE INDEX uq_deployments_running
    ON deployments (project_id) WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE deployment_events (
    id BIGSERIAL PRIMARY KEY,
    deployment_id UUID NOT NULL REFERENCES deployments(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    line TEXT NOT NULL
);
CREATE INDEX idx_deployment_events_dep ON deployment_events (deployment_id, id);
