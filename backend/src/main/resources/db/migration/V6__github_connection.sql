CREATE TABLE github_connections (
    id UUID PRIMARY KEY,
    github_login VARCHAR(255) NOT NULL,
    encrypted_token TEXT NOT NULL,
    encrypted_refresh TEXT,
    scopes VARCHAR(512),
    connected_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
