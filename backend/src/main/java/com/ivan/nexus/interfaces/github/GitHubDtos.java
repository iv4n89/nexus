package com.ivan.nexus.interfaces.github;

import com.ivan.nexus.application.github.CreateProjectFromRepo;
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

    public record CreateProjectRequest(
            String owner,
            String repo,
            String branch,
            String projectId) {
    }

    public record CreateProjectResponse(
            String projectId,
            String name,
            String owner,
            String repo,
            String branch,
            boolean hasNexusYml,
            boolean hasDeploySh) {
        public static CreateProjectResponse from(CreateProjectFromRepo.Result result) {
            return new CreateProjectResponse(
                    result.projectId(),
                    result.name(),
                    result.owner(),
                    result.repo(),
                    result.branch(),
                    result.hasNexusYml(),
                    result.hasDeploySh());
        }
    }
}
