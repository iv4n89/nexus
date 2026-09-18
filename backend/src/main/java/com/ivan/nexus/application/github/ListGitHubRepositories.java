package com.ivan.nexus.application.github;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListGitHubRepositories {
    private final RequireGitHubAccessToken accessToken;
    private final GitHubClient gitHubClient;

    public ListGitHubRepositories(RequireGitHubAccessToken accessToken, GitHubClient gitHubClient) {
        this.accessToken = accessToken;
        this.gitHubClient = gitHubClient;
    }

    @Transactional(readOnly = true)
    public List<GitHubRepositorySummary> execute() {
        return gitHubClient.listRepositories(accessToken.execute());
    }
}
