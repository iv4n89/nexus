package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AcknowledgeAlert {
    private final AlertStore alerts;
    private final UserDirectory users;
    private final RecordAudit recordAudit;

    public AcknowledgeAlert(
            AlertStore alerts,
            UserDirectory users,
            RecordAudit recordAudit) {
        this.alerts = alerts;
        this.users = users;
        this.recordAudit = recordAudit;
    }

    @Transactional
    public Alert execute(UUID id, String username, String ip) {
        Alert alert = alerts.acknowledge(id, Instant.now())
                .orElseThrow(() -> new DomainException(NexusErrorCode.ALERT_NOT_FOUND, "Alert not found"));
        UUID userId = users.findIdByUsername(username).orElse(null);
        recordAudit.execute(
                userId,
                AuditAction.ALERT_ACKNOWLEDGE,
                alert.projectId(),
                alert.serviceId(),
                ip,
                Map.of("alertId", id.toString()));
        return alert;
    }
}
