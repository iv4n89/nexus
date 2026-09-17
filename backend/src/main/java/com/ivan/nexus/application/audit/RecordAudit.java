package com.ivan.nexus.application.audit;

import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.audit.AuditEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RecordAudit {
    private final AuditStore auditStore;

    public RecordAudit(AuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public void execute(
            UUID userId,
            AuditAction action,
            String projectId,
            String serviceId,
            String ip,
            Map<String, Object> metadata) {
        auditStore.append(new AuditEvent(
                UUID.randomUUID(),
                userId,
                action,
                projectId,
                serviceId,
                ip,
                withoutPassword(metadata),
                Instant.now()));
    }

    private static Map<String, Object> withoutPassword(Map<String, Object> metadata) {
        Map<String, Object> safe = new HashMap<>();
        if (metadata != null) {
            safe.putAll(metadata);
            safe.remove("password");
        }
        return safe;
    }
}
