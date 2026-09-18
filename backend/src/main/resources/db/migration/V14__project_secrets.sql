CREATE TABLE project_env_vars (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    encrypted_value TEXT NOT NULL,
    secret BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_project_env_vars_project_name UNIQUE (project_id, name)
);

CREATE INDEX idx_project_env_vars_project ON project_env_vars (project_id);
