package com.ivan.nexus.infrastructure.persistence.security;

import com.ivan.nexus.application.security.SecurityFindingStore;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaSecurityFindingStoreTest {
    private SecurityFindingJpaRepository repository;
    private SecurityFindingStore store;

    @BeforeEach
    void setUp() {
        repository = mock(SecurityFindingJpaRepository.class);
        store = new JpaSecurityFindingStore(repository);
    }

    @Test
    void upsertCreatesWhenFingerprintMissing() {
        SecurityFinding finding = sample(UUID.randomUUID(), SecurityFindingStatus.OPEN);
        when(repository.findByProjectIdAndFingerprint(finding.projectId(), finding.fingerprint()))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityFinding saved = store.upsertByFingerprint(finding);

        assertThat(saved).isEqualTo(finding);
        ArgumentCaptor<SecurityFindingEntity> captor = ArgumentCaptor.forClass(SecurityFindingEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFingerprint()).isEqualTo(finding.fingerprint());
    }

    @Test
    void upsertUpdatesLastSeenAndReopensResolved() {
        Instant first = Instant.parse("2026-09-01T00:00:00Z");
        Instant again = Instant.parse("2026-09-18T00:00:00Z");
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        SecurityFindingEntity existing = new SecurityFindingEntity(
                id,
                "lab",
                SecuritySeverity.HIGH,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.1",
                "CVE-1",
                "fp",
                SecurityFindingStatus.RESOLVED,
                first,
                first);
        when(repository.findByProjectIdAndFingerprint("lab", "fp")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityFinding incoming = new SecurityFinding(
                UUID.randomUUID(),
                "lab",
                SecuritySeverity.CRITICAL,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.2",
                "CVE-1",
                "fp",
                SecurityFindingStatus.OPEN,
                again,
                again);

        SecurityFinding result = store.upsertByFingerprint(incoming);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.status()).isEqualTo(SecurityFindingStatus.OPEN);
        assertThat(result.severity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(result.fixedVersion()).isEqualTo("1.0.2");
        assertThat(result.firstSeen()).isEqualTo(first);
        assertThat(result.lastSeen()).isEqualTo(again);
    }

    @Test
    void listByProjectMapsEntities() {
        SecurityFinding finding = sample(UUID.randomUUID(), SecurityFindingStatus.OPEN);
        when(repository.findByProjectIdOrderByLastSeenDesc("lab"))
                .thenReturn(List.of(entity(finding)));

        assertThat(store.listByProject("lab")).containsExactly(finding);
    }

    @Test
    void acknowledgeMutatesStatus() {
        SecurityFinding open = sample(UUID.randomUUID(), SecurityFindingStatus.OPEN);
        SecurityFindingEntity entity = entity(open);
        when(repository.findById(open.id())).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SecurityFinding result = store.acknowledge(open.id(), Instant.parse("2026-09-18T10:00:00Z"))
                .orElseThrow();

        assertThat(result.status()).isEqualTo(SecurityFindingStatus.ACKNOWLEDGED);
        assertThat(entity.getStatus()).isEqualTo(SecurityFindingStatus.ACKNOWLEDGED);
    }

    private static SecurityFinding sample(UUID id, SecurityFindingStatus status) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return new SecurityFinding(
                id,
                "lab",
                SecuritySeverity.MEDIUM,
                "trivy",
                "lodash",
                "4.17.20",
                "4.17.21",
                "Prototype pollution",
                "fp-lodash",
                status,
                now,
                now);
    }

    private static SecurityFindingEntity entity(SecurityFinding finding) {
        return new SecurityFindingEntity(
                finding.id(),
                finding.projectId(),
                finding.severity(),
                finding.source(),
                finding.packageName(),
                finding.installedVersion(),
                finding.fixedVersion(),
                finding.title(),
                finding.fingerprint(),
                finding.status(),
                finding.firstSeen(),
                finding.lastSeen());
    }
}
