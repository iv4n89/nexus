package com.ivan.nexus.domain.traffic;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficSeriesBinningTest {

    private static final List<TrafficMinuteBucket> MINUTES = List.of(
            minute("lab", "web", "2026-09-18T10:00:00Z", 10, 0),
            minute("lab", "web", "2026-09-18T10:14:00Z", 5, 1),
            minute("lab", "api", "2026-09-18T10:15:00Z", 7, 0));

    @Test
    void binsFifteenMinutesWhenWindowExceeds24h() {
        List<TrafficSeriesPoint> series = TrafficSeriesPoint.bin(MINUTES, Duration.ofMinutes(15));

        assertThat(series).hasSize(2);
        assertThat(series.getFirst().requests()).isEqualTo(15);
        assertThat(series.getFirst().status5xx()).isEqualTo(1);
    }

    @Test
    void reportSplitsServices() {
        TrafficReport report = TrafficReport.fromMinutes(
                "lab",
                Instant.parse("2026-09-18T10:00:00Z"),
                Instant.parse("2026-09-18T11:00:00Z"),
                MINUTES,
                Duration.ofMinutes(1));

        assertThat(report.totals().requests()).isEqualTo(22);
        assertThat(report.services()).extracting(TrafficServiceBreakdown::serviceId)
                .containsExactly("web", "api");
        assertThat(report.services().getFirst().requests()).isEqualTo(15);
        assertThat(report.services().get(1).requests()).isEqualTo(7);
    }

    @Test
    void overviewRanksProjectsAndUsesGlobalTotals() {
        List<TrafficMinuteBucket> minutes = List.of(
                minute("alpha", "web", "2026-09-18T10:00:00Z", 30, 0),
                minute("beta", "api", "2026-09-18T10:01:00Z", 50, 2),
                minute("gamma", "web", "2026-09-18T10:02:00Z", 10, 0));

        TrafficOverview overview = TrafficOverview.fromMinutes(
                Instant.parse("2026-09-18T10:00:00Z"),
                Instant.parse("2026-09-18T11:00:00Z"),
                minutes,
                Duration.ofMinutes(1));

        assertThat(overview.totals().projectId()).isEqualTo("_global");
        assertThat(overview.totals().requests()).isEqualTo(90);
        assertThat(overview.projects()).extracting(TrafficProjectRanking::projectId)
                .containsExactly("beta", "alpha", "gamma");
        assertThat(overview.projects().getFirst().requests()).isEqualTo(50);
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
}
