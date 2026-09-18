ALTER TABLE projects
    ADD COLUMN github_owner VARCHAR(255),
    ADD COLUMN github_repo VARCHAR(255),
    ADD COLUMN github_branch VARCHAR(255);
