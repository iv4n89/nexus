package com.ivan.nexus.application.github;

public record GitHubOAuthToken(
        String accessToken,
        String refreshToken,
        String scope,
        String tokenType) {
}
