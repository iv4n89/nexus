package com.ivan.nexus.application.github;

public record GitHubRepositorySummary(
        long id,
        String fullName,
        String name,
        String ownerLogin,
        String defaultBranch,
        boolean privateRepository) {
}
