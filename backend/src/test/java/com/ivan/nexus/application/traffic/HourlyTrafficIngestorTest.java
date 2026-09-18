package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficHourlyBucket;
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

class HourlyTrafficIngestorTest {

    private FakeTrafficStore store;
    private HourlyTrafficIngestor ingestor;

    @BeforeEach
    void setUp() {
        store = new FakeTrafficStore();
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T10:30:00Z"), ZoneOffset.UTC);
        ingestor = new HourlyTrafficIngestor(store, clock);
    }

    @Test
    void aggregatesRawEventsIntoHourlyBucket() {
        ingestor.ingestRaw("lab", "web", 200, 100, 25);
        ingestor.ingestRaw("lab", "web", 500, 50, 75);

        TrafficHourlyBucket bucket = store.findBucket(
                        "lab", "web", Instant.parse("2026-09-18T10:00:00Z"))
                .orElseThrow();

        assertThat(bucket.requests()).isEqualTo(2);
        assertThat(bucket.bytesOut()).isEqualTo(150);
        assertThat(bucket.status2xx()).isEqualTo(1);
        assertThat(bucket.status5xx()).isEqualTo(1);
        assertThat(bucket.latencyAvg()).isEqualTo(50.0);
    }

    @Test
    void getProjectTrafficReturnsAggregatedSnapshot() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC);
        store.save(TrafficHourlyBucket.empty(
                        UUID.randomUUID(), "lab", "", Instant.parse("2026-09-18T10:00:00Z"))
                .ingest(200, 10, 10));
        store.save(TrafficHourlyBucket.empty(
                        UUID.randomUUID(), "lab", "", Instant.parse("2026-09-18T11:00:00Z"))
                .ingest(200, 20, 30));

        TrafficSnapshot snapshot = new GetProjectTraffic(store, clock).execute("lab", 24);

        assertThat(snapshot.requests()).isEqualTo(2);
        assertThat(snapshot.bytesOut()).isEqualTo(30);
        assertThat(snapshot.latencyAvg()).isEqualTo(20.0);
    }

    private static final class FakeTrafficStore implements TrafficStore {
        private final List<TrafficHourlyBucket> buckets = new ArrayList<>();

        @Override
        public Optional<TrafficHourlyBucket> findBucket(String projectId, String domainId, Instant bucketStart) {
            return buckets.stream()
                    .filter(bucket -> bucket.projectId().equals(projectId)
                            && bucket.domainId().equals(TrafficHourlyBucket.normalizeDomainId(domainId))
                            && bucket.bucketStart().equals(bucketStart))
                    .findFirst();
        }

        @Override
        public TrafficHourlyBucket save(TrafficHourlyBucket bucket) {
            buckets.removeIf(existing -> existing.id().equals(bucket.id())
                    || (existing.projectId().equals(bucket.projectId())
                            && existing.domainId().equals(bucket.domainId())
                            && existing.bucketStart().equals(bucket.bucketStart())));
            buckets.add(bucket);
            return bucket;
        }

        @Override
        public List<TrafficHourlyBucket> findByProjectSince(String projectId, Instant since) {
            return buckets.stream()
                    .filter(bucket -> bucket.projectId().equals(projectId)
                            && !bucket.bucketStart().isBefore(since))
                    .toList();
        }

        @Override
        public TrafficSnapshot snapshot(String projectId, Instant from, Instant to) {
            return TrafficSnapshot.aggregate(projectId, from, to, findByProjectSince(projectId, from));
        }
    }
}
