package com.ivan.nexus.application.github;

public record ProjectGitHubLink(
        String projectId,
        String owner,
        String repo,
        String branch,
        boolean autoDeploy,
        String lastRemoteSha) {
}
