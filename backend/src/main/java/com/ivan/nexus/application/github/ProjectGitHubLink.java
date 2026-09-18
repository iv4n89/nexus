package com.ivan.nexus.application.github;

public record ProjectGitHubLink(
        String projectId,
        String owner,
        String repo,
        String branch,
        boolean autodeployEnabled,
        String lastRemoteSha) {

    public static ProjectGitHubLink of(String projectId, String owner, String repo, String branch) {
        return new ProjectGitHubLink(projectId, owner, repo, branch, false, null);
    }
}
