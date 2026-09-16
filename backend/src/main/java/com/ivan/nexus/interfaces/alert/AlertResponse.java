package com.ivan.nexus.interfaces.alert;

import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventEntity;

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

    public static AlertResponse from(AlertEventEntity entity) {
        return new AlertResponse(
                entity.getId(),
                entity.getRule() == null ? null : entity.getRule().getId(),
                entity.getProjectId(),
                entity.getServiceId(),
                entity.getStatus(),
                entity.getMessage(),
                entity.getOpenedAt(),
                entity.getAcknowledgedAt(),
                entity.getResolvedAt(),
                entity.getRule() == null ? null : entity.getRule().getType());
    }
}
