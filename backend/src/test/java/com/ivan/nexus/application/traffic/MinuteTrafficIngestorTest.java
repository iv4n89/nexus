package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MinuteTrafficIngestorTest {

    private FakeTrafficStore store;
    private MinuteTrafficIngestor ingestor;

    @BeforeEach
    void setUp() {
        store = new FakeTrafficStore();
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T10:31:40Z"), ZoneOffset.UTC);
        ingestor = new MinuteTrafficIngestor(store, clock);
    }

    @Test
    void aggregatesRawEventsIntoCurrentUtcMinute() {
        ingestor.ingestRaw("lab", "web", "app.example.com", 200, 100, 25);
        ingestor.ingestRaw("lab", "web", "app.example.com", 500, 50, 75);

        TrafficMinuteBucket bucket = store.findBucket(
                        "lab", "web", "app.example.com", Instant.parse("2026-09-18T10:31:00Z"))
                .orElseThrow();

        assertThat(bucket.bucketStart()).isEqualTo(Instant.parse("2026-09-18T10:31:00Z"));
        assertThat(bucket.requests()).isEqualTo(2);
        assertThat(bucket.bytesOut()).isEqualTo(150);
        assertThat(bucket.status2xx()).isEqualTo(1);
        assertThat(bucket.status5xx()).isEqualTo(1);
        assertThat(bucket.latencyAvgMs()).isEqualTo(50.0);
        assertThat(bucket.latencyMaxMs()).isEqualTo(75.0);
    }

    @Test
    void getProjectTrafficReturnsAggregatedSnapshot() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC);
        store.save(TrafficMinuteBucket.empty(
                        UUID.randomUUID(), "lab", "app", "", Instant.parse("2026-09-18T10:00:00Z"))
                .ingest(200, 10, 10));
        store.save(TrafficMinuteBucket.empty(
                        UUID.randomUUID(), "lab", "app", "", Instant.parse("2026-09-18T11:00:00Z"))
                .ingest(200, 20, 30));

        TrafficSnapshot snapshot = new GetProjectTraffic(store, clock).execute("lab", 24);

        assertThat(snapshot.requests()).isEqualTo(2);
        assertThat(snapshot.bytesOut()).isEqualTo(30);
        assertThat(snapshot.latencyAvgMs()).isEqualTo(20.0);
        assertThat(snapshot.latencyP95Ms()).isEqualTo(30.0);
        assertThat(snapshot.topEndpoints()).isEmpty();
    }

    private static final class FakeTrafficStore implements TrafficStore {
        private final List<TrafficMinuteBucket> buckets = new ArrayList<>();

        @Override
        public Optional<TrafficMinuteBucket> findBucket(
                String projectId, String serviceId, String host, Instant bucketStart) {
            String service = TrafficMinuteBucket.normalizeServiceId(serviceId);
            String normalizedHost = TrafficMinuteBucket.normalizeHost(host);
            return buckets.stream()
                    .filter(bucket -> bucket.projectId().equals(projectId)
                            && bucket.serviceId().equals(service)
                            && bucket.host().equals(normalizedHost)
                            && bucket.bucketStart().equals(bucketStart))
                    .findFirst();
        }

        @Override
        public TrafficMinuteBucket save(TrafficMinuteBucket bucket) {
            buckets.removeIf(existing -> existing.id().equals(bucket.id())
                    || (existing.projectId().equals(bucket.projectId())
                            && existing.serviceId().equals(bucket.serviceId())
                            && existing.host().equals(bucket.host())
                            && existing.bucketStart().equals(bucket.bucketStart())));
            buckets.add(bucket);
            return bucket;
        }

        @Override
        public List<TrafficMinuteBucket> findByProjectSince(String projectId, Instant since) {
            return buckets.stream()
                    .filter(bucket -> bucket.projectId().equals(projectId)
                            && !bucket.bucketStart().isBefore(since))
                    .toList();
        }

        @Override
        public List<TrafficMinuteBucket> findSince(Instant since) {
            return buckets.stream()
                    .filter(bucket -> !bucket.bucketStart().isBefore(since))
                    .toList();
        }

        @Override
        public TrafficSnapshot snapshot(String projectId, Instant from, Instant to) {
            List<TrafficMinuteBucket> window = buckets.stream()
                    .filter(bucket -> bucket.projectId().equals(projectId)
                            && !bucket.bucketStart().isBefore(from)
                            && bucket.bucketStart().isBefore(to))
                    .toList();
            return TrafficSnapshot.aggregateMinutes(projectId, from, to, window);
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            int before = buckets.size();
            buckets.removeIf(bucket -> bucket.bucketStart().isBefore(cutoff));
            return before - buckets.size();
        }
    }
}
