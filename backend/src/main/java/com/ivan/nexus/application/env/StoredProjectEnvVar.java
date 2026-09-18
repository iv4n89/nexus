package com.ivan.nexus.application.env;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence shape for a project environment variable. Value is always ciphertext at rest.
 */
public record StoredProjectEnvVar(
        UUID id,
        String projectId,
        String name,
        String encryptedValue,
        boolean secret,
        Instant createdAt,
        Instant updatedAt) {
}
