package com.ivan.nexus.application.github;

import java.util.List;

/**
 * Outbound port for GitHub OAuth and identity. Repository listing is reserved
 * for Phase B2 ({@link #listRepositories(String)}).
 */
public interface GitHubClient {
    String buildAuthorizeUrl(String state);

    GitHubOAuthToken exchangeCode(String code);

    GitHubIdentity getAuthenticatedUser(String accessToken);

    /**
     * Lists repositories visible to the authenticated user.
     * Not implemented in Phase B1.
     */
    List<GitHubRepositorySummary> listRepositories(String accessToken);
}
