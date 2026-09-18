package com.ivan.nexus.infrastructure.persistence.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaTrafficStoreTest {
    private TrafficMinuteJpaRepository repository;
    private JpaTrafficStore store;

    @BeforeEach
    void setUp() {
        repository = mock(TrafficMinuteJpaRepository.class);
        store = new JpaTrafficStore(repository);
    }

    @Test
    void savePersistsNewBucket() {
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(
                        UUID.randomUUID(),
                        "lab",
                        "web",
                        "app.example.com",
                        Instant.parse("2026-09-18T10:31:00Z"))
                .ingest(200, 12, 33);
        when(repository.findById(bucket.id())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrafficMinuteBucket saved = store.save(bucket);

        assertThat(saved.requests()).isEqualTo(1);
        assertThat(saved.bytesOut()).isEqualTo(12);
        assertThat(saved.latencyAvgMs()).isEqualTo(33.0);
        assertThat(saved.latencyMaxMs()).isEqualTo(33.0);
        verify(repository).save(any(TrafficMinuteEntity.class));
    }

    @Test
    void snapshotMapsEntities() {
        Instant from = Instant.parse("2026-09-18T09:00:00Z");
        Instant to = Instant.parse("2026-09-18T12:00:00Z");
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(UUID.randomUUID(), "lab", "app", "", from)
                .ingest(200, 5, 10);
        when(repository.findByProjectIdAndBucketStartGreaterThanEqualOrderByBucketStartAsc("lab", from))
                .thenReturn(List.of(new TrafficMinuteEntity(bucket)));

        TrafficSnapshot snapshot = store.snapshot("lab", from, to);

        assertThat(snapshot.requests()).isEqualTo(1);
        assertThat(snapshot.bytesOut()).isEqualTo(5);
        assertThat(snapshot.latencyAvgMs()).isEqualTo(10.0);
        assertThat(snapshot.latencyP95Ms()).isEqualTo(10.0);
        assertThat(snapshot.topEndpoints()).isEmpty();
    }

    @Test
    void findSinceDelegatesToRepository() {
        Instant since = Instant.parse("2026-09-18T10:00:00Z");
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(
                        UUID.randomUUID(), "lab", "web", "app.example.com", since)
                .ingest(200, 8, 12);
        when(repository.findByBucketStartGreaterThanEqualOrderByBucketStartAsc(since))
                .thenReturn(List.of(new TrafficMinuteEntity(bucket)));

        List<TrafficMinuteBucket> found = store.findSince(since);

        assertThat(found).containsExactly(bucket);
    }

    @Test
    void deleteOlderThanDelegatesToRepository() {
        Instant cutoff = Instant.parse("2026-09-11T00:00:00Z");
        when(repository.deleteByBucketStartBefore(cutoff)).thenReturn(3);

        assertThat(store.deleteOlderThan(cutoff)).isEqualTo(3);
        verify(repository).deleteByBucketStartBefore(cutoff);
    }

    @Test
    void findBucketNormalizesServiceAndHost() {
        Instant start = Instant.parse("2026-09-18T10:31:00Z");
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(
                UUID.randomUUID(), "lab", "app", "", start);
        when(repository.findByProjectIdAndServiceIdAndHostAndBucketStart("lab", "app", "", start))
                .thenReturn(Optional.of(new TrafficMinuteEntity(bucket)));

        Optional<TrafficMinuteBucket> found = store.findBucket("lab", "  ", null, start);

        assertThat(found).contains(bucket);
        verify(repository).findByProjectIdAndServiceIdAndHostAndBucketStart("lab", "app", "", start);
    }
}
