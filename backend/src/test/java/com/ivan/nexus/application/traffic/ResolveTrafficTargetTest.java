package com.ivan.nexus.application.traffic;

import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResolveTrafficTargetTest {

    @Mock
    DomainStore domains;

    private ResolveTrafficTarget useCase() {
        return new ResolveTrafficTarget(domains);
    }

    @Test
    void resolvesMappedDomain() {
        given(domains.findByHostname("app.example.com"))
                .willReturn(Optional.of(siteDomain("lab", "app.example.com", "web")));

        ResolveTrafficTarget.ResolvedTarget result = useCase().execute("App.Example.COM");

        assertThat(result.projectId()).isEqualTo("lab");
        assertThat(result.serviceId()).isEqualTo("web");
        assertThat(result.host()).isEqualTo("app.example.com");
        verify(domains).findByHostname("app.example.com");
    }

    @Test
    void resolvesUnknownHostToUnmapped() {
        given(domains.findByHostname("unknown.example.com")).willReturn(Optional.empty());

        ResolveTrafficTarget.ResolvedTarget result = useCase().execute("unknown.example.com");

        assertThat(result.projectId()).isEqualTo(TrafficMinuteBucket.UNMAPPED_PROJECT);
        assertThat(result.serviceId()).isEqualTo(TrafficMinuteBucket.UNKNOWN_SERVICE);
        assertThat(result.host()).isEqualTo("unknown.example.com");
    }

    @Test
    void resolvesBlankHostToUnmappedWithEmptyHost() {
        ResolveTrafficTarget useCase = useCase();

        assertThat(useCase.execute(null))
                .isEqualTo(new ResolveTrafficTarget.ResolvedTarget(
                        TrafficMinuteBucket.UNMAPPED_PROJECT,
                        TrafficMinuteBucket.UNKNOWN_SERVICE,
                        ""));
        assertThat(useCase.execute(""))
                .isEqualTo(new ResolveTrafficTarget.ResolvedTarget(
                        TrafficMinuteBucket.UNMAPPED_PROJECT,
                        TrafficMinuteBucket.UNKNOWN_SERVICE,
                        ""));
        assertThat(useCase.execute("   "))
                .isEqualTo(new ResolveTrafficTarget.ResolvedTarget(
                        TrafficMinuteBucket.UNMAPPED_PROJECT,
                        TrafficMinuteBucket.UNKNOWN_SERVICE,
                        ""));
    }

    @Test
    void stripsPortBeforeLookup() {
        given(domains.findByHostname("0nexus.duckdns.org"))
                .willReturn(Optional.of(siteDomain("nexus", "0nexus.duckdns.org", "app")));

        ResolveTrafficTarget.ResolvedTarget result = useCase().execute("0nexus.duckdns.org:443");

        assertThat(result.projectId()).isEqualTo("nexus");
        assertThat(result.serviceId()).isEqualTo("app");
        assertThat(result.host()).isEqualTo("0nexus.duckdns.org");
        verify(domains).findByHostname("0nexus.duckdns.org");
    }

    private static SiteDomain siteDomain(String projectId, String hostname, String serviceName) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new SiteDomain(
                UUID.randomUUID(),
                projectId,
                hostname,
                serviceName,
                443,
                now,
                now,
                CertStatus.ACTIVE);
    }
}
