package com.ivan.nexus.infrastructure.persistence.log;

import com.ivan.nexus.application.log.FingerprintStore;
import com.ivan.nexus.application.log.StoredErrorFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaFingerprintStoreTest {

    @Mock
    LogErrorFingerprintJpaRepository repository;

    FingerprintStore store;

    @BeforeEach
    void setUp() {
        store = new JpaFingerprintStore(repository);
    }

    @Test
    void saveMapsRecordToEntityAndBack() {
        Instant seen = Instant.parse("2026-01-01T00:00:00Z");
        StoredErrorFingerprint record = fingerprint(seen, seen, 1L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StoredErrorFingerprint saved = store.save(record);

        ArgumentCaptor<LogErrorFingerprintEntity> captor = ArgumentCaptor.forClass(LogErrorFingerprintEntity.class);
        verify(repository).save(captor.capture());
        LogErrorFingerprintEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo(record.id());
        assertThat(entity.getProjectId()).isEqualTo("lab");
        assertThat(entity.getServiceId()).isEqualTo("web");
        assertThat(entity.getFingerprint()).isEqualTo("hash");
        assertThat(entity.getFirstSeen()).isEqualTo(seen);
        assertThat(entity.getLastSeen()).isEqualTo(seen);
        assertThat(entity.getCount()).isEqualTo(1L);
        assertThat(entity.getSampleMessage()).isEqualTo("ERROR boom");
        assertThat(saved).isEqualTo(record);
    }

    @Test
    void findMapsEntityToRecord() {
        Instant seen = Instant.parse("2026-01-01T00:00:00Z");
        StoredErrorFingerprint expected = fingerprint(seen, seen, 2L);
        when(repository.findByProjectIdAndServiceIdAndFingerprint("lab", "web", "hash"))
                .thenReturn(Optional.of(entity(expected)));

        assertThat(store.findByProjectIdAndServiceIdAndFingerprint("lab", "web", "hash"))
                .contains(expected);
    }

    @Test
    void recordHitPersistsIncrementedCountAndLastSeen() {
        Instant firstSeen = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-01-01T01:00:00Z");
        StoredErrorFingerprint original = fingerprint(firstSeen, firstSeen, 3L);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        store.save(original.recordHit(later));

        ArgumentCaptor<LogErrorFingerprintEntity> captor = ArgumentCaptor.forClass(LogErrorFingerprintEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCount()).isEqualTo(4L);
        assertThat(captor.getValue().getLastSeen()).isEqualTo(later);
        assertThat(captor.getValue().getFirstSeen()).isEqualTo(firstSeen);
        assertThat(original.count()).isEqualTo(3L);
        assertThat(original.lastSeen()).isEqualTo(firstSeen);
    }

    @Test
    void deleteByLastSeenBeforeDelegates() {
        Instant cutoff = Instant.parse("2026-06-18T03:00:00Z");
        when(repository.deleteByLastSeenBefore(cutoff)).thenReturn(2L);

        assertThat(store.deleteByLastSeenBefore(cutoff)).isEqualTo(2L);
        verify(repository).deleteByLastSeenBefore(cutoff);
    }

    private static StoredErrorFingerprint fingerprint(Instant firstSeen, Instant lastSeen, long count) {
        return new StoredErrorFingerprint(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "lab",
                "web",
                "hash",
                firstSeen,
                lastSeen,
                count,
                "ERROR boom");
    }

    private static LogErrorFingerprintEntity entity(StoredErrorFingerprint fingerprint) {
        return new LogErrorFingerprintEntity(
                fingerprint.id(),
                fingerprint.projectId(),
                fingerprint.serviceId(),
                fingerprint.fingerprint(),
                fingerprint.firstSeen(),
                fingerprint.lastSeen(),
                fingerprint.count(),
                fingerprint.sampleMessage());
    }
}
