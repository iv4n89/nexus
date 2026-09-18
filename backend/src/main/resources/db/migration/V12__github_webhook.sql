ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS github_owner VARCHAR(255),
    ADD COLUMN IF NOT EXISTS github_repo VARCHAR(255),
    ADD COLUMN IF NOT EXISTS github_branch VARCHAR(255),
    ADD COLUMN IF NOT EXISTS auto_deploy BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS github_last_remote_sha VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_projects_github_link
    ON projects (github_owner, github_repo, github_branch);
