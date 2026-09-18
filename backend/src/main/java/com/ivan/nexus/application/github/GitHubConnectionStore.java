package com.ivan.nexus.application.github;

import java.util.Optional;

/**
 * Single-installation GitHub connection store (one row for the Nexus VPS).
 */
public interface GitHubConnectionStore {
    Optional<StoredGitHubConnection> load();

    void save(StoredGitHubConnection connection);

    void clear();
}
