package com.ivan.nexus.application.site;

import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListProjectDomainsTest {

    @Mock
    DomainStore domains;

    @Test
    void delegatesToStore() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        SiteDomain domain = new SiteDomain(
                UUID.randomUUID(), "lab", "app.example.com", "api", 8080, now, now, CertStatus.PENDING);
        given(domains.findByProjectId("lab")).willReturn(List.of(domain));

        assertThat(new ListProjectDomains(domains).execute("lab")).containsExactly(domain);
        verify(domains).findByProjectId("lab");
    }
}
