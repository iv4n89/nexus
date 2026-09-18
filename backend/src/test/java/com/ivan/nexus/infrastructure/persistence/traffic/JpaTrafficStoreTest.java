package com.ivan.nexus.infrastructure.persistence.traffic;

import com.ivan.nexus.domain.traffic.TrafficHourlyBucket;
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
    private TrafficHourlyJpaRepository repository;
    private JpaTrafficStore store;

    @BeforeEach
    void setUp() {
        repository = mock(TrafficHourlyJpaRepository.class);
        store = new JpaTrafficStore(repository);
    }

    @Test
    void savePersistsNewBucket() {
        TrafficHourlyBucket bucket = TrafficHourlyBucket.empty(
                        UUID.randomUUID(), "lab", "web", Instant.parse("2026-09-18T10:00:00Z"))
                .ingest(200, 12, 33, "/ok");
        when(repository.findById(bucket.id())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrafficHourlyBucket saved = store.save(bucket);

        assertThat(saved.requests()).isEqualTo(1);
        assertThat(saved.bytesOut()).isEqualTo(12);
        assertThat(saved.topEndpoints()).hasSize(1);
        verify(repository).save(any(TrafficHourlyEntity.class));
    }

    @Test
    void snapshotMapsEntities() {
        Instant from = Instant.parse("2026-09-18T09:00:00Z");
        Instant to = Instant.parse("2026-09-18T12:00:00Z");
        TrafficHourlyBucket bucket = TrafficHourlyBucket.empty(UUID.randomUUID(), "lab", "", from)
                .ingest(200, 5, 10, "/x");
        when(repository.findByProjectIdAndBucketStartGreaterThanEqualOrderByBucketStartAsc("lab", from))
                .thenReturn(List.of(new TrafficHourlyEntity(bucket)));

        TrafficSnapshot snapshot = store.snapshot("lab", from, to);

        assertThat(snapshot.requests()).isEqualTo(1);
        assertThat(snapshot.bytesOut()).isEqualTo(5);
        assertThat(snapshot.latencyAvgMs()).isEqualTo(10.0);
        assertThat(snapshot.topEndpoints()).hasSize(1);
    }
}
