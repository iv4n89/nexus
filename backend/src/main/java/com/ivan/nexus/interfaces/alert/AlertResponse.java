package com.ivan.nexus.interfaces.alert;

import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
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

    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.id(),
                alert.ruleId(),
                alert.projectId(),
                alert.serviceId(),
                alert.status(),
                alert.message(),
                alert.openedAt(),
                alert.acknowledgedAt(),
                alert.resolvedAt(),
                alert.type());
    }
}
