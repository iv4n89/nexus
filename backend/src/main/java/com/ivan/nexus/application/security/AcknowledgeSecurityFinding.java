package com.ivan.nexus.application.security;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AcknowledgeSecurityFinding {
    private final SecurityFindingStore findings;
    private final UserDirectory users;
    private final RecordAudit recordAudit;

    public AcknowledgeSecurityFinding(
            SecurityFindingStore findings,
            UserDirectory users,
            RecordAudit recordAudit) {
        this.findings = findings;
        this.users = users;
        this.recordAudit = recordAudit;
    }

    @Transactional
    public SecurityFinding execute(String projectId, UUID findingId, String username, String ip) {
        SecurityFinding finding = findings.findById(findingId)
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.SECURITY_FINDING_NOT_FOUND, "Security finding not found"));
        if (!Objects.equals(projectId, finding.projectId())) {
            throw new DomainException(
                    NexusErrorCode.SECURITY_FINDING_NOT_FOUND, "Security finding not found");
        }

        SecurityFinding acknowledged = findings.acknowledge(findingId, Instant.now())
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.SECURITY_FINDING_NOT_FOUND, "Security finding not found"));

        UUID userId = users.findIdByUsername(username).orElse(null);
        recordAudit.execute(
                userId,
                AuditAction.SECURITY_FINDING_ACKNOWLEDGE,
                acknowledged.projectId(),
                null,
                ip,
                Map.of("findingId", findingId.toString(), "fingerprint", acknowledged.fingerprint()));
        return acknowledged;
    }
}
