package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficSnapshot;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EnforceTrafficRetentionTest {

    private static final Instant NOW = Instant.parse("2026-09-18T12:00:00Z");

    private FakeTrafficStore store;
    private NexusProperties properties;
    private EnforceTrafficRetention retention;

    @BeforeEach
    void setUp() {
        store = new FakeTrafficStore();
        properties = new NexusProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        retention = new EnforceTrafficRetention(store, properties, clock);
    }

    @Test
    void deletesBucketsOlderThanRetentionWindow() {
        store.save(bucket(Instant.parse("2026-09-10T00:00:00Z")));
        store.save(bucket(Instant.parse("2026-09-12T00:00:00Z")));

        retention.execute();

        assertThat(store.findSince(Instant.EPOCH))
                .extracting(TrafficMinuteBucket::bucketStart)
                .containsExactly(Instant.parse("2026-09-12T00:00:00Z"));
    }

    @Test
    void scheduledOnConfiguredCron() throws Exception {
        Scheduled scheduled = EnforceTrafficRetention.class.getMethod("execute").getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${nexus.traffic.retention-cron:0 15 3 * * *}");
    }

    private static TrafficMinuteBucket bucket(Instant bucketStart) {
        return TrafficMinuteBucket.empty(UUID.randomUUID(), "lab", "web", "", bucketStart);
    }

    private static final class FakeTrafficStore implements TrafficStore {
        private final List<TrafficMinuteBucket> buckets = new ArrayList<>();

        @Override
        public Optional<TrafficMinuteBucket> findBucket(
                String projectId, String serviceId, String host, Instant bucketStart) {
            return Optional.empty();
        }

        @Override
        public TrafficMinuteBucket save(TrafficMinuteBucket bucket) {
            buckets.add(bucket);
            return bucket;
        }

        @Override
        public List<TrafficMinuteBucket> findByProjectSince(String projectId, Instant since) {
            return List.of();
        }

        @Override
        public List<TrafficMinuteBucket> findSince(Instant since) {
            return buckets.stream()
                    .filter(bucket -> !bucket.bucketStart().isBefore(since))
                    .toList();
        }

        @Override
        public TrafficSnapshot snapshot(String projectId, Instant from, Instant to) {
            return TrafficSnapshot.aggregateMinutes(projectId, from, to, List.of());
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            int before = buckets.size();
            buckets.removeIf(bucket -> bucket.bucketStart().isBefore(cutoff));
            return before - buckets.size();
        }
    }
}
