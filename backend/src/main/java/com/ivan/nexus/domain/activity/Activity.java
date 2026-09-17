package com.ivan.nexus.domain.activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Activity(
        UUID id,
        Instant createdAt,
        ActivityType type,
        String projectId,
        String serviceId,
        String message,
        Map<String, Object> metadata) {
}
