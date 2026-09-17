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

    public Alert acknowledge(Instant at) {
        return new Alert(
                id,
                ruleId,
                projectId,
                serviceId,
                AlertStatus.ACKNOWLEDGED,
                message,
                openedAt,
                at,
                resolvedAt,
                type);
    }

    public Alert resolve(Instant at) {
        return new Alert(
                id,
                ruleId,
                projectId,
                serviceId,
                AlertStatus.RESOLVED,
                message,
                openedAt,
                acknowledgedAt,
                at,
                type);
    }
}
