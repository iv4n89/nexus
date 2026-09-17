package com.ivan.nexus.interfaces.activity;

import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;

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

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(
                activity.id(),
                activity.createdAt(),
                activity.type(),
                activity.projectId(),
                activity.serviceId(),
                activity.message(),
                activity.metadata());
    }
}
