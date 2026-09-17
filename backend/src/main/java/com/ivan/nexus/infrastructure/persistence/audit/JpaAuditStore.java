package com.ivan.nexus.infrastructure.persistence.audit;

import com.ivan.nexus.application.audit.AuditStore;
import com.ivan.nexus.domain.audit.AuditEvent;
import org.springframework.stereotype.Component;

@Component
public class JpaAuditStore implements AuditStore {
    private final AuditEventJpaRepository auditEvents;

    public JpaAuditStore(AuditEventJpaRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Override
    public void append(AuditEvent event) {
        auditEvents.save(new AuditEventEntity(
                event.id(),
                event.userId(),
                event.action(),
                event.projectId(),
                event.serviceId(),
                event.ip(),
                event.metadata(),
                event.createdAt()));
    }
}
