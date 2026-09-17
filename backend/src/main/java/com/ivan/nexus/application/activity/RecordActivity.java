package com.ivan.nexus.application.activity;

import com.ivan.nexus.domain.activity.Activity;
import com.ivan.nexus.domain.activity.ActivityType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RecordActivity {
    private final ActivityStore store;
    private final ActivityPublisher publisher;

    public RecordActivity(ActivityStore store, ActivityPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    public void execute(
            ActivityType type,
            String projectId,
            String serviceId,
            String message,
            Map<String, Object> metadata) {
        Map<String, Object> payload = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        Activity activity = new Activity(
                UUID.randomUUID(),
                Instant.now(),
                type,
                projectId,
                serviceId,
                message,
                payload);
        store.append(activity);
        publisher.publish(activity);
    }
}
