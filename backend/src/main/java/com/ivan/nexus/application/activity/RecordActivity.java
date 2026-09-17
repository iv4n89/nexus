package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventEntity;
import com.ivan.nexus.infrastructure.persistence.activity.ActivityEventJpaRepository;
import com.ivan.nexus.infrastructure.sse.ActivityHub;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RecordActivity {
    private final ActivityEventJpaRepository events;
    private final ActivityHub hub;

    public RecordActivity(ActivityEventJpaRepository events, ActivityHub hub) {
        this.events = events;
        this.hub = hub;
    }

    public void execute(
            ActivityType type,
            String projectId,
            String serviceId,
            String message,
            Map<String, Object> metadata) {
        Map<String, Object> payload = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        ActivityEventEntity entity = new ActivityEventEntity(
                UUID.randomUUID(),
                Instant.now(),
                type,
                projectId,
                serviceId,
                message,
                payload);
        events.save(entity);
        hub.publish(entity.toDomain());
    }
}
