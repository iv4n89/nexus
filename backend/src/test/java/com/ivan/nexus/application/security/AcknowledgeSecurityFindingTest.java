package com.ivan.nexus.application.security;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AcknowledgeSecurityFindingTest {

    @Mock
    SecurityFindingStore findings;
    @Mock
    UserDirectory users;
    @Mock
    RecordAudit recordAudit;

    @Test
    void missingFindingThrowsNotFound() {
        UUID id = UUID.randomUUID();
        given(findings.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                        new AcknowledgeSecurityFinding(findings, users, recordAudit)
                                .execute("lab", id, "admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.SECURITY_FINDING_NOT_FOUND));

        verify(findings, never()).acknowledge(any(), any());
    }

    @Test
    void wrongProjectThrowsNotFound() {
        UUID id = UUID.randomUUID();
        given(findings.findById(id)).willReturn(Optional.of(finding(id, "other")));

        assertThatThrownBy(() ->
                        new AcknowledgeSecurityFinding(findings, users, recordAudit)
                                .execute("lab", id, "admin", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.SECURITY_FINDING_NOT_FOUND));
    }

    @Test
    void acknowledgesAndWritesAudit() {
        UUID id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        SecurityFinding open = finding(id, "lab");
        SecurityFinding acknowledged = open.acknowledge(Instant.parse("2026-09-18T10:00:00Z"));
        given(findings.findById(id)).willReturn(Optional.of(open));
        given(findings.acknowledge(eq(id), any())).willReturn(Optional.of(acknowledged));
        given(users.findIdByUsername("admin")).willReturn(Optional.empty());

        SecurityFinding result = new AcknowledgeSecurityFinding(findings, users, recordAudit)
                .execute("lab", id, "admin", "10.0.0.1");

        assertThat(result.status()).isEqualTo(SecurityFindingStatus.ACKNOWLEDGED);
        var order = inOrder(findings, users, recordAudit);
        order.verify(findings).findById(id);
        order.verify(findings).acknowledge(eq(id), any());
        order.verify(users).findIdByUsername("admin");
        order.verify(recordAudit).execute(
                null,
                AuditAction.SECURITY_FINDING_ACKNOWLEDGE,
                "lab",
                null,
                "10.0.0.1",
                Map.of("findingId", id.toString(), "fingerprint", acknowledged.fingerprint()));
    }

    private static SecurityFinding finding(UUID id, String projectId) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return new SecurityFinding(
                id,
                projectId,
                SecuritySeverity.HIGH,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.1",
                "CVE-1",
                "fp-1",
                SecurityFindingStatus.OPEN,
                now,
                now);
    }
}
