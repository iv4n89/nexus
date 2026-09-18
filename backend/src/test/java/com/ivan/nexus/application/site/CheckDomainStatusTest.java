package com.ivan.nexus.application.site;

import com.ivan.nexus.application.caddy.CertificateManager;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CheckDomainStatusTest {

    @Mock
    DomainDnsResolver dnsResolver;
    @Mock
    DomainHttpsProbe httpsProbe;
    @Mock
    CertificateManager certificateManager;

    private final FakeDomainStore domains = new FakeDomainStore();
    private CheckDomainStatus check;
    private SiteDomain domain;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        domain = new SiteDomain(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "lab",
                "app.example.com",
                "frontend",
                3000,
                now,
                now,
                CertStatus.PENDING);
        domains.save(domain);
        check = new CheckDomainStatus(
                domains,
                dnsResolver,
                Optional.of(httpsProbe),
                Optional.of(certificateManager));
    }

    @Test
    void dnsFailureSetsError() {
        given(dnsResolver.resolves("app.example.com")).willReturn(false);

        SiteDomain updated = check.execute(domain.id());

        assertThat(updated.certStatus()).isEqualTo(CertStatus.ERROR);
        assertThat(domains.findById(domain.id()).orElseThrow().certStatus()).isEqualTo(CertStatus.ERROR);
        verify(httpsProbe, never()).probe("app.example.com");
    }

    @Test
    void httpsSuccessSetsActive() {
        given(dnsResolver.resolves("app.example.com")).willReturn(true);
        given(certificateManager.ensureCertificate(domain)).willReturn(CertStatus.PENDING);
        given(httpsProbe.probe("app.example.com")).willReturn(true);

        SiteDomain updated = check.execute(domain);

        assertThat(updated.certStatus()).isEqualTo(CertStatus.ACTIVE);
    }

    @Test
    void httpsFailureKeepsPendingWhenCertPending() {
        given(dnsResolver.resolves("app.example.com")).willReturn(true);
        given(certificateManager.ensureCertificate(domain)).willReturn(CertStatus.PENDING);
        given(httpsProbe.probe("app.example.com")).willReturn(false);

        SiteDomain updated = check.execute(domain);

        assertThat(updated.certStatus()).isEqualTo(CertStatus.PENDING);
        // unchanged — still PENDING, no save needed for status equality
        assertThat(updated).isSameAs(domain);
    }

    @Test
    void missingDomainThrows() {
        assertThatThrownBy(() -> check.execute(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.DOMAIN_NOT_FOUND));
    }

    @Test
    void withoutHttpsProbeUsesCertificateManagerStatus() {
        CheckDomainStatus withoutProbe = new CheckDomainStatus(
                domains,
                dnsResolver,
                Optional.empty(),
                Optional.of(certificateManager));
        given(dnsResolver.resolves("app.example.com")).willReturn(true);
        given(certificateManager.ensureCertificate(domain)).willReturn(CertStatus.ACTIVE);

        SiteDomain updated = withoutProbe.execute(domain);

        assertThat(updated.certStatus()).isEqualTo(CertStatus.ACTIVE);
    }
}
