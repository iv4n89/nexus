package com.ivan.nexus.application.caddy;

import com.ivan.nexus.application.site.FakeDomainStore;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ReloadProjectDomainsTest {

    @Mock
    CaddyConfigWriter caddyConfigWriter;
    @Mock
    CertificateManager certificateManager;

    @Test
    void reloadsWriterAndEnsuresCertificates() {
        FakeDomainStore domains = new FakeDomainStore();
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        SiteDomain domain = new SiteDomain(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "pantry",
                "pantry.example.com",
                "frontend",
                3000,
                now,
                now,
                CertStatus.PENDING);
        domains.save(domain);
        given(certificateManager.ensureCertificate(domain)).willReturn(CertStatus.PENDING);

        ReloadProjectDomains useCase = new ReloadProjectDomains(domains, caddyConfigWriter, certificateManager);
        useCase.execute("pantry");

        InOrder order = inOrder(caddyConfigWriter, certificateManager);
        order.verify(caddyConfigWriter).writeProjectSites(eq("pantry"), eq(List.of(domain)));
        order.verify(certificateManager).ensureCertificate(domain);
        assertThat(domains.findById(domain.id()).orElseThrow().certStatus()).isEqualTo(CertStatus.PENDING);
    }

    @Test
    void persistsCertificateStatusWhenChanged() {
        FakeDomainStore domains = new FakeDomainStore();
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        SiteDomain domain = new SiteDomain(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "pantry",
                "pantry.example.com",
                "frontend",
                3000,
                now,
                now,
                CertStatus.PENDING);
        domains.save(domain);
        given(certificateManager.ensureCertificate(domain)).willReturn(CertStatus.ACTIVE);

        new ReloadProjectDomains(domains, caddyConfigWriter, certificateManager).execute("pantry");

        assertThat(domains.findById(domain.id()).orElseThrow().certStatus()).isEqualTo(CertStatus.ACTIVE);
    }

    @Test
    void swallowsWriterFailureSoDomainListingCanSucceed() {
        FakeDomainStore domains = new FakeDomainStore();
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        SiteDomain domain = new SiteDomain(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "nexus",
                "0nexus.duckdns.org",
                "app",
                80,
                now,
                now,
                CertStatus.PENDING);
        domains.save(domain);
        doThrow(new UncheckedIOException("Failed to write Caddy snippet", new IOException("Read-only file system")))
                .when(caddyConfigWriter)
                .writeProjectSites(eq("nexus"), eq(List.of(domain)));

        new ReloadProjectDomains(domains, caddyConfigWriter, certificateManager).execute("nexus");
    }
}
