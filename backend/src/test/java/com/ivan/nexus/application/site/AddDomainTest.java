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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
class AddDomainTest {

    @Mock
    DomainStore domains;
    @Mock
    UserDirectory users;
    @Mock
    RecordAudit recordAudit;
    @Mock
    RecordActivity recordActivity;

    @Test
    void rejectsInvalidHostname() {
        AddDomain addDomain = new AddDomain(domains, users, recordAudit, recordActivity);

        assertThatThrownBy(() -> addDomain.execute("lab", "bad_host", "api", 8080, "admin", "10.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DOMAIN_HOSTNAME_INVALID));
        verify(domains, never()).save(any());
    }

    @Test
    void rejectsDuplicateHostname() {
        given(domains.findByHostname("app.example.com")).willReturn(Optional.of(existing()));

        AddDomain addDomain = new AddDomain(domains, users, recordAudit, recordActivity);

        assertThatThrownBy(() -> addDomain.execute("lab", "App.Example.COM", "api", 8080, "admin", "10.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DOMAIN_HOSTNAME_DUPLICATE));
        verify(domains, never()).save(any());
    }

    @Test
    void savesNormalizedDomainAndRecordsAuditAndActivity() {
        given(domains.findByHostname("app.example.com")).willReturn(Optional.empty());
        given(domains.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(users.findIdByUsername("admin")).willReturn(Optional.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));

        SiteDomain result = new AddDomain(domains, users, recordAudit, recordActivity)
                .execute("lab", "App.Example.COM.", "api", 8080, "admin", "10.0.0.1");

        assertThat(result.hostname()).isEqualTo("app.example.com");
        assertThat(result.projectId()).isEqualTo("lab");
        assertThat(result.serviceName()).isEqualTo("api");
        assertThat(result.targetPort()).isEqualTo(8080);
        assertThat(result.certStatus()).isEqualTo(CertStatus.PENDING);

        ArgumentCaptor<SiteDomain> saved = ArgumentCaptor.forClass(SiteDomain.class);
        var order = inOrder(domains, users, recordAudit, recordActivity);
        order.verify(domains).findByHostname("app.example.com");
        order.verify(domains).save(saved.capture());
        order.verify(users).findIdByUsername("admin");
        order.verify(recordAudit).execute(
                eq(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                eq(AuditAction.DOMAIN_ADD),
                eq("lab"),
                eq("api"),
                eq("10.0.0.1"),
                any());
        order.verify(recordActivity).execute(
                eq(ActivityType.DOMAIN_ADDED),
                eq("lab"),
                eq("api"),
                eq("Domain added: app.example.com"),
                any());

        assertThat(saved.getValue().id()).isEqualTo(result.id());
        assertThat(result.createdAt()).isNotNull();
    }

    private static SiteDomain existing() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new SiteDomain(
                UUID.randomUUID(),
                "other",
                "app.example.com",
                "web",
                80,
                now,
                now,
                CertStatus.ACTIVE);
    }
}
