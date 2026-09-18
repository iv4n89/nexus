package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.traffic.TrafficMinuteBucket;
import com.ivan.nexus.domain.traffic.TrafficReport;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProjectTrafficTest {

    private FakeTrafficStore store;
    private Clock clock;

    @BeforeEach
    void setUp() {
        store = new FakeTrafficStore();
        clock = Clock.fixed(Instant.parse("2026-09-18T12:30:00Z"), ZoneOffset.UTC);
    }

    @Test
    void rejectsHoursBelowOne() {
        assertThatThrownBy(() -> new GetProjectTraffic(store, clock).execute("lab", 0))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.OPERATION_NOT_ALLOWED);
    }

    @Test
    void rejectsHoursAbove168() {
        assertThatThrownBy(() -> new GetProjectTraffic(store, clock).execute("lab", 169))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.OPERATION_NOT_ALLOWED);
    }

    @Test
    void doesNotTruncateWindowToHour() {
        store.save(minute("lab", "web", "2026-09-18T11:00:00Z", 10, 0));
        store.save(minute("lab", "web", "2026-09-18T11:35:00Z", 5, 0));

        TrafficReport report = new GetProjectTraffic(store, clock).execute("lab", 1);

        assertThat(report.from()).isEqualTo(Instant.parse("2026-09-18T11:30:00Z"));
        assertThat(report.to()).isEqualTo(Instant.parse("2026-09-18T12:30:00Z"));
        assertThat(report.totals().requests()).isEqualTo(5);
    }

    @Test
    void binsOneMinuteWhenHoursAre24() {
        store.save(minute("lab", "web", "2026-09-18T10:00:00Z", 10, 0));
        store.save(minute("lab", "web", "2026-09-18T10:14:00Z", 5, 1));

        TrafficReport report = new GetProjectTraffic(store, clock).execute("lab", 24);

        assertThat(report.series()).hasSize(2);
        assertThat(report.series().getFirst().t()).isEqualTo(Instant.parse("2026-09-18T10:00:00Z"));
        assertThat(report.series().get(1).t()).isEqualTo(Instant.parse("2026-09-18T10:14:00Z"));
    }

    @Test
    void binsFifteenMinutesWhenHoursAre168() {
        store.save(minute("lab", "web", "2026-09-18T10:00:00Z", 10, 0));
        store.save(minute("lab", "web", "2026-09-18T10:14:00Z", 5, 1));

        TrafficReport report = new GetProjectTraffic(store, clock).execute("lab", 168);

        assertThat(report.series()).hasSize(1);
        assertThat(report.series().getFirst().t()).isEqualTo(Instant.parse("2026-09-18T10:00:00Z"));
        assertThat(report.series().getFirst().requests()).isEqualTo(15);
    }

    private static TrafficMinuteBucket minute(
            String projectId, String serviceId, String bucketStart, long requests, long status5xx) {
        long status2xx = requests - status5xx;
        return new TrafficMinuteBucket(
                UUID.randomUUID(),
                projectId,
                serviceId,
                "",
                Instant.parse(bucketStart),
                requests,
                0,
                0,
                status2xx,
                0,
                0,
                status5xx,
                0.0,
                null);
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
