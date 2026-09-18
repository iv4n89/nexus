package com.ivan.nexus.application.github;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;

@Service
public class StartGitHubOAuth {
    private final GitHubClient gitHubClient;
    private final GitHubOAuthStateStore stateStore;

    public StartGitHubOAuth(GitHubClient gitHubClient, GitHubOAuthStateStore stateStore) {
        this.gitHubClient = gitHubClient;
        this.stateStore = stateStore;
    }

    public String execute() {
        try {
            String state = stateStore.issue();
            return gitHubClient.buildAuthorizeUrl(state);
        } catch (IllegalStateException ex) {
            throw new DomainException(NexusErrorCode.GITHUB_NOT_CONFIGURED, ex.getMessage());
        }
    }
}
