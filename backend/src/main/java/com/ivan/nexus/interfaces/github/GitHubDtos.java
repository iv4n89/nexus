package com.ivan.nexus.interfaces.github;

import com.ivan.nexus.application.github.GitHubConnectionView;

import java.time.Instant;

public final class GitHubDtos {
    private GitHubDtos() {
    }

    public record StatusResponse(
            boolean connected,
            String githubLogin,
            String scopes,
            Instant connectedAt) {
        public static StatusResponse from(GitHubConnectionView view) {
            return new StatusResponse(
                    view.connected(),
                    view.githubLogin(),
                    view.scopes(),
                    view.connectedAt());
        }
    }

    public record AuthorizeUrlResponse(String authorizeUrl) {
    }
}
