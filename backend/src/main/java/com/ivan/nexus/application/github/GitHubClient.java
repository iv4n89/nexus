package com.ivan.nexus.application.github;

import java.util.List;

/**
 * Outbound port for GitHub OAuth, identity, and repository metadata.
 */
public interface GitHubClient {
    String buildAuthorizeUrl(String state);

    GitHubOAuthToken exchangeCode(String code);

    GitHubIdentity getAuthenticatedUser(String accessToken);

    /**
     * Lists repositories visible to the authenticated user.
     * Not implemented until Phase B2.
     */
    List<GitHubRepositorySummary> listRepositories(String accessToken);

    /**
     * Returns the commit SHA at the tip of {@code branch}.
     */
    String getBranchHead(String accessToken, String owner, String repo, String branch);
}
