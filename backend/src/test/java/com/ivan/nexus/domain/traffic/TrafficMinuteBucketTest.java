package com.ivan.nexus.domain.traffic;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficMinuteBucketTest {
    @Test
    void ingestAccumulatesStatusBytesAndLatency() {
        Instant start = Instant.parse("2026-09-18T10:31:00Z");
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "lab", "web", "app.example.com", start)
                .ingest(200, 100, 20)
                .ingest(500, 50, 80);

        assertThat(bucket.requests()).isEqualTo(2);
        assertThat(bucket.bytesOut()).isEqualTo(150);
        assertThat(bucket.status2xx()).isEqualTo(1);
        assertThat(bucket.status5xx()).isEqualTo(1);
        assertThat(bucket.latencyAvgMs()).isEqualTo(50.0);
        assertThat(bucket.latencyMaxMs()).isEqualTo(80.0);
    }

    @Test
    void normalizeBlanks() {
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(
                UUID.randomUUID(), "lab", null, null, Instant.parse("2026-09-18T10:31:00Z"));
        assertThat(bucket.serviceId()).isEqualTo("app");
        assertThat(bucket.host()).isEqualTo("");
    }
}
