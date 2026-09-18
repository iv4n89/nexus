package com.ivan.nexus.application.github;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted GitHub connection. Token fields are opaque ciphertext only.
 */
public record StoredGitHubConnection(
        UUID id,
        String githubLogin,
        String encryptedToken,
        String encryptedRefreshToken,
        String scopes,
        Instant connectedAt,
        Instant updatedAt) {
}
