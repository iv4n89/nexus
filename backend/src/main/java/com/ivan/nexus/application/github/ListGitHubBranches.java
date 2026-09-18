package com.ivan.nexus.application.github;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListGitHubBranches {
    private final RequireGitHubAccessToken accessToken;
    private final GitHubClient gitHubClient;

    public ListGitHubBranches(RequireGitHubAccessToken accessToken, GitHubClient gitHubClient) {
        this.accessToken = accessToken;
        this.gitHubClient = gitHubClient;
    }

    @Transactional(readOnly = true)
    public List<GitHubBranchSummary> execute(String owner, String repo) {
        if (owner == null || owner.isBlank() || repo == null || repo.isBlank()) {
            throw new DomainException(NexusErrorCode.GITHUB_OAUTH_FAILED, "owner and repo are required");
        }
        return gitHubClient.listBranches(accessToken.execute(), owner.trim(), repo.trim());
    }
}
