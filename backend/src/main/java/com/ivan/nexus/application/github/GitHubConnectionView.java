package com.ivan.nexus.application.github;

import java.time.Instant;

public record GitHubConnectionView(
        boolean connected,
        String githubLogin,
        String scopes,
        Instant connectedAt) {

    public static GitHubConnectionView disconnected() {
        return new GitHubConnectionView(false, null, null, null);
    }

    public static GitHubConnectionView from(StoredGitHubConnection connection) {
        return new GitHubConnectionView(
                true,
                connection.githubLogin(),
                connection.scopes(),
                connection.connectedAt());
    }
}
