package com.ivan.nexus.application.env;

import java.time.Instant;
import java.util.UUID;

/**
 * Masked API view. Secret values are never returned as plaintext.
 */
public record ProjectEnvVarView(
        UUID id,
        String projectId,
        String name,
        boolean secret,
        String value,
        Instant createdAt,
        Instant updatedAt) {
}
