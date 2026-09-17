package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventEntity;
import com.ivan.nexus.infrastructure.persistence.alert.AlertEventJpaRepository;
import com.ivan.nexus.infrastructure.persistence.user.UserEntity;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AcknowledgeAlert {
    private final AlertEventJpaRepository events;
    private final UserJpaRepository users;
    private final RecordAudit recordAudit;

    public AcknowledgeAlert(
            AlertEventJpaRepository events,
            UserJpaRepository users,
            RecordAudit recordAudit) {
        this.events = events;
        this.users = users;
        this.recordAudit = recordAudit;
    }

    @Transactional
    public Alert execute(UUID id, String username, String ip) {
        AlertEventEntity event = events.findById(id)
                .orElseThrow(() -> new DomainException(NexusErrorCode.ALERT_NOT_FOUND, "Alert not found"));
        event.acknowledge(Instant.now());
        events.save(event);
        UUID userId = users.findByUsername(username).map(UserEntity::getId).orElse(null);
        recordAudit.execute(
                userId,
                AuditAction.ALERT_ACKNOWLEDGE,
                event.getProjectId(),
                event.getServiceId(),
                ip,
                Map.of("alertId", id.toString()));
        return event.toDomain();
    }
}
