package com.ivan.nexus.infrastructure.persistence.site;

import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaDomainStoreTest {
    private SiteDomainJpaRepository repository;
    private DomainStore store;

    @BeforeEach
    void setUp() {
        repository = mock(SiteDomainJpaRepository.class);
        store = new JpaDomainStore(repository);
    }

    @Test
    void saveMapsDomainToEntityAndBack() {
        SiteDomain domain = domain("app.example.com");
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        SiteDomain saved = store.save(domain);

        ArgumentCaptor<SiteDomainEntity> captor = ArgumentCaptor.forClass(SiteDomainEntity.class);
        verify(repository).save(captor.capture());
        SiteDomainEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(domain.id());
        assertThat(entity.getProjectId()).isEqualTo(domain.projectId());
        assertThat(entity.getHostname()).isEqualTo(domain.hostname());
        assertThat(entity.getServiceName()).isEqualTo(domain.serviceName());
        assertThat(entity.getTargetPort()).isEqualTo(domain.targetPort());
        assertThat(entity.getCertStatus()).isEqualTo(domain.certStatus());
        assertThat(entity.getCreatedAt()).isEqualTo(domain.createdAt());
        assertThat(entity.getUpdatedAt()).isEqualTo(domain.updatedAt());
        assertThat(saved).isEqualTo(domain);
    }

    @Test
    void findByProjectIdMapsEntities() {
        SiteDomain domain = domain("app.example.com");
        when(repository.findByProjectIdOrderByCreatedAtDesc("lab")).thenReturn(List.of(entity(domain)));

        assertThat(store.findByProjectId("lab")).containsExactly(domain);
    }

    @Test
    void findByHostnameMapsOptional() {
        SiteDomain domain = domain("app.example.com");
        when(repository.findByHostname("app.example.com")).thenReturn(Optional.of(entity(domain)));

        assertThat(store.findByHostname("app.example.com")).contains(domain);
    }

    @Test
    void deleteDelegates() {
        UUID id = UUID.randomUUID();
        store.delete(id);
        verify(repository).deleteById(id);
    }

    private static SiteDomain domain(String hostname) {
        Instant now = Instant.parse("2026-09-18T10:00:00Z");
        return new SiteDomain(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "lab",
                hostname,
                "api",
                8080,
                now,
                now,
                CertStatus.PENDING);
    }

    private static SiteDomainEntity entity(SiteDomain domain) {
        return new SiteDomainEntity(
                domain.id(),
                domain.projectId(),
                domain.hostname(),
                domain.serviceName(),
                domain.targetPort(),
                domain.certStatus(),
                domain.createdAt(),
                domain.updatedAt());
    }
}
