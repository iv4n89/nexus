package com.ivan.nexus.application.github;

/**
 * Placeholder for Phase B2 repository listing. Kept on the client port surface
 * so adapters can grow without reshaping the application boundary.
 */
public record GitHubRepositorySummary(
        long id,
        String fullName,
        String defaultBranch,
        boolean privateRepository) {
}
