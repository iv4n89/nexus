package com.ivan.nexus.application.github;

public record ProjectGitHubStatusView(
        String projectId,
        String owner,
        String repo,
        String branch,
        String remoteHeadSha,
        String deployedCommitSha,
        boolean upToDate,
        boolean autodeployEnabled) {
}
