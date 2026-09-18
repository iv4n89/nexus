ALTER TABLE projects
    ADD COLUMN autodeploy_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN github_last_remote_sha VARCHAR(64);

CREATE INDEX idx_projects_github_link
    ON projects (github_owner, github_repo, github_branch);
