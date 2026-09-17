package com.ivan.nexus.domain.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        UUID userId,
        AuditAction action,
        String projectId,
        String serviceId,
        String ip,
        Map<String, Object> metadata,
        Instant createdAt) {

    public AuditEvent {
        metadata = Collections.unmodifiableMap(new HashMap<>(metadata == null ? Map.of() : metadata));
    }
}
