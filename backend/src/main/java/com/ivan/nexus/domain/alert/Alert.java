package com.ivan.nexus.domain.alert;

import java.time.Instant;
import java.util.UUID;

public record Alert(
        UUID id,
        UUID ruleId,
        String projectId,
        String serviceId,
        AlertStatus status,
        String message,
        Instant openedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        AlertType type) {
}
