package com.ivan.nexus.application.site;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RemoveDomainTest {

    @Mock
    DomainStore domains;
    @Mock
    UserDirectory users;
    @Mock
    RecordAudit recordAudit;
    @Mock
    RecordActivity recordActivity;

    @Test
    void missingDomainThrowsNotFound() {
        UUID id = UUID.randomUUID();
        given(domains.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> new RemoveDomain(domains, users, recordAudit, recordActivity, Optional.empty())
                .execute("lab", id, "admin", "10.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DOMAIN_NOT_FOUND));
        verify(domains, never()).delete(id);
    }

    @Test
    void wrongProjectThrowsNotFound() {
        UUID id = UUID.randomUUID();
        given(domains.findById(id)).willReturn(Optional.of(domain(id, "other")));

        assertThatThrownBy(() -> new RemoveDomain(domains, users, recordAudit, recordActivity, Optional.empty())
                .execute("lab", id, "admin", "10.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DOMAIN_NOT_FOUND));
        verify(domains, never()).delete(id);
    }

    @Test
    void deletesAndRecordsAuditAndActivity() {
        UUID id = UUID.randomUUID();
        given(domains.findById(id)).willReturn(Optional.of(domain(id, "lab")));
        given(users.findIdByUsername("admin")).willReturn(Optional.empty());

        new RemoveDomain(domains, users, recordAudit, recordActivity, Optional.empty())
                .execute("lab", id, "admin", "10.0.0.1");

        var order = inOrder(domains, users, recordAudit, recordActivity);
        order.verify(domains).findById(id);
        order.verify(domains).delete(id);
        order.verify(users).findIdByUsername("admin");
        order.verify(recordAudit).execute(
                null,
                AuditAction.DOMAIN_REMOVE,
                "lab",
                "api",
                "10.0.0.1",
                Map.of("domainId", id.toString(), "hostname", "app.example.com"));
        order.verify(recordActivity).execute(
                ActivityType.DOMAIN_REMOVED,
                "lab",
                "api",
                "Domain removed: app.example.com",
                Map.of("domainId", id.toString(), "hostname", "app.example.com"));
    }

    private static SiteDomain domain(UUID id, String projectId) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new SiteDomain(id, projectId, "app.example.com", "api", 8080, now, now, CertStatus.PENDING);
    }
}
