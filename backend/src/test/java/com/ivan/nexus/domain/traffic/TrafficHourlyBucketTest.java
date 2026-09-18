package com.ivan.nexus.domain.traffic;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficHourlyBucketTest {

    @Test
    void ingestUpdatesCountersAndRunningAverage() {
        Instant start = Instant.parse("2026-09-18T10:00:00Z");
        TrafficHourlyBucket bucket = TrafficHourlyBucket.empty(UUID.randomUUID(), "lab", null, start)
                .ingest(200, 100, 40)
                .ingest(500, 50, 60);

        assertThat(bucket.requests()).isEqualTo(2);
        assertThat(bucket.bytesOut()).isEqualTo(150);
        assertThat(bucket.status2xx()).isEqualTo(1);
        assertThat(bucket.status5xx()).isEqualTo(1);
        assertThat(bucket.latencyAvg()).isEqualTo(50.0);
        assertThat(bucket.domainId()).isEmpty();
    }

    @Test
    void snapshotAggregatesBuckets() {
        Instant from = Instant.parse("2026-09-18T08:00:00Z");
        Instant to = Instant.parse("2026-09-18T12:00:00Z");
        TrafficHourlyBucket a = TrafficHourlyBucket.empty(UUID.randomUUID(), "lab", "", from)
                .ingest(200, 10, 20);
        TrafficHourlyBucket b = TrafficHourlyBucket.empty(
                        UUID.randomUUID(), "lab", "", Instant.parse("2026-09-18T09:00:00Z"))
                .ingest(404, 20, 40);

        TrafficSnapshot snapshot = TrafficSnapshot.aggregate("lab", from, to, List.of(a, b));

        assertThat(snapshot.requests()).isEqualTo(2);
        assertThat(snapshot.bytesOut()).isEqualTo(30);
        assertThat(snapshot.status2xx()).isEqualTo(1);
        assertThat(snapshot.status4xx()).isEqualTo(1);
        assertThat(snapshot.latencyAvg()).isEqualTo(30.0);
    }
}
