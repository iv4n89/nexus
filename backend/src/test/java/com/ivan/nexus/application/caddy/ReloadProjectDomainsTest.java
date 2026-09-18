package com.ivan.nexus.application.caddy;

import com.ivan.nexus.application.site.FakeDomainStore;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
    }
}
