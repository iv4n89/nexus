package com.ivan.nexus.domain.traffic;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficHourlyBucketTest {

    @Test
    void ingestUpdatesCountersLatencyAndTopEndpoints() {
        Instant start = Instant.parse("2026-09-18T10:00:00Z");
        TrafficHourlyBucket bucket = TrafficHourlyBucket.empty(UUID.randomUUID(), "lab", null, start)
                .ingest(200, 100, 40, "/api/a")
                .ingest(500, 50, 60, "/api/a")
                .ingest(200, 10, 100, "/api/b");

        assertThat(bucket.requests()).isEqualTo(3);
        assertThat(bucket.bytesOut()).isEqualTo(160);
        assertThat(bucket.status2xx()).isEqualTo(2);
        assertThat(bucket.status5xx()).isEqualTo(1);
        assertThat(bucket.latencyAvgMs()).isEqualTo(200.0 / 3);
        assertThat(bucket.latencyP95Ms()).isEqualTo(100.0);
        assertThat(bucket.domainId()).isEmpty();
        assertThat(bucket.topEndpoints()).hasSize(2);
        assertThat(bucket.topEndpoints().getFirst().path()).isEqualTo("/api/a");
        assertThat(bucket.topEndpoints().getFirst().requests()).isEqualTo(2);
    }

    @Test
    void snapshotAggregatesBucketsAndEndpoints() {
        Instant from = Instant.parse("2026-09-18T08:00:00Z");
        Instant to = Instant.parse("2026-09-18T12:00:00Z");
        TrafficHourlyBucket a = TrafficHourlyBucket.empty(UUID.randomUUID(), "lab", "", from)
                .ingest(200, 10, 20, "/x");
        TrafficHourlyBucket b = TrafficHourlyBucket.empty(
                        UUID.randomUUID(), "lab", "", Instant.parse("2026-09-18T09:00:00Z"))
                .ingest(404, 20, 40, "/x");

        TrafficSnapshot snapshot = TrafficSnapshot.aggregate("lab", from, to, List.of(a, b));

        assertThat(snapshot.requests()).isEqualTo(2);
        assertThat(snapshot.bytesOut()).isEqualTo(30);
        assertThat(snapshot.status2xx()).isEqualTo(1);
        assertThat(snapshot.status4xx()).isEqualTo(1);
        assertThat(snapshot.latencyAvgMs()).isEqualTo(30.0);
        assertThat(snapshot.latencyP95Ms()).isEqualTo(40.0);
        assertThat(snapshot.topEndpoints()).hasSize(1);
        assertThat(snapshot.topEndpoints().getFirst().requests()).isEqualTo(2);
    }
}
