package com.ivan.nexus.interfaces.activity;

import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        Instant createdAt,
        ActivityType type,
        String projectId,
        String serviceId,
        String message,
        Map<String, Object> metadata) {

    public static ActivityResponse from(ActivityEventEntity entity) {
        return new ActivityResponse(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getType(),
                entity.getProjectId(),
                entity.getServiceId(),
                entity.getMessage(),
                entity.getMetadata());
    }
}
