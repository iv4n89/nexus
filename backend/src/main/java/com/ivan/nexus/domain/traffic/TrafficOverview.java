package com.ivan.nexus.domain.traffic;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TrafficOverview(
        Instant from,
        Instant to,
        TrafficSnapshot totals,
        List<TrafficSeriesPoint> series,
        List<TrafficProjectRanking> projects) {

    public static final String GLOBAL_PROJECT_ID = "_global";

    public TrafficOverview {
        series = List.copyOf(series == null ? List.of() : series);
        projects = List.copyOf(projects == null ? List.of() : projects);
    }

    public static TrafficOverview fromMinutes(
            Instant from, Instant to, List<TrafficMinuteBucket> minutes, Duration binSize) {
        TrafficSnapshot totals = TrafficSnapshot.aggregateMinutes(GLOBAL_PROJECT_ID, from, to, minutes);
        List<TrafficSeriesPoint> series = TrafficSeriesPoint.bin(minutes, binSize);
        List<TrafficProjectRanking> projects = rankProjects(minutes);
        return new TrafficOverview(from, to, totals, series, projects);
    }

    private static List<TrafficProjectRanking> rankProjects(List<TrafficMinuteBucket> minutes) {
        Map<String, ProjectAccumulator> byProject = new HashMap<>();
        for (TrafficMinuteBucket bucket : minutes) {
            byProject.computeIfAbsent(bucket.projectId(), ProjectAccumulator::new).add(bucket);
        }
        List<TrafficProjectRanking> rankings = new ArrayList<>();
        for (ProjectAccumulator accumulator : byProject.values()) {
            rankings.add(accumulator.toRanking());
        }
        rankings.sort(Comparator.comparingLong(TrafficProjectRanking::requests).reversed()
                .thenComparing(TrafficProjectRanking::projectId));
        return List.copyOf(rankings);
    }

    private static final class ProjectAccumulator {
        private final String projectId;
        private long requests;
        private long status5xx;
        private double latencyWeighted;

        private ProjectAccumulator(String projectId) {
            this.projectId = projectId;
        }

        private void add(TrafficMinuteBucket bucket) {
            requests += bucket.requests();
            status5xx += bucket.status5xx();
            latencyWeighted += bucket.latencyAvgMs() * bucket.requests();
        }

        private TrafficProjectRanking toRanking() {
            double latencyAvg = requests == 0 ? 0.0 : latencyWeighted / requests;
            return new TrafficProjectRanking(projectId, requests, status5xx, latencyAvg);
        }
    }
}
