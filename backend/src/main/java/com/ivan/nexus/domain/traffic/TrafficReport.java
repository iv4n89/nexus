package com.ivan.nexus.domain.traffic;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TrafficReport(
        String projectId,
        Instant from,
        Instant to,
        TrafficSnapshot totals,
        List<TrafficSeriesPoint> series,
        List<TrafficServiceBreakdown> services) {

    public TrafficReport {
        series = List.copyOf(series == null ? List.of() : series);
        services = List.copyOf(services == null ? List.of() : services);
    }

    public static TrafficReport fromMinutes(
            String projectId,
            Instant from,
            Instant to,
            List<TrafficMinuteBucket> minutes,
            Duration binSize) {
        TrafficSnapshot totals = TrafficSnapshot.aggregateMinutes(projectId, from, to, minutes);
        List<TrafficSeriesPoint> series = TrafficSeriesPoint.bin(minutes, binSize);
        List<TrafficServiceBreakdown> services = groupByService(minutes);
        return new TrafficReport(projectId, from, to, totals, series, services);
    }

    private static List<TrafficServiceBreakdown> groupByService(List<TrafficMinuteBucket> minutes) {
        Map<String, ServiceAccumulator> byService = new HashMap<>();
        for (TrafficMinuteBucket bucket : minutes) {
            byService.computeIfAbsent(bucket.serviceId(), ServiceAccumulator::new).add(bucket);
        }
        List<TrafficServiceBreakdown> services = new ArrayList<>();
        for (ServiceAccumulator accumulator : byService.values()) {
            services.add(accumulator.toBreakdown());
        }
        services.sort(Comparator.comparingLong(TrafficServiceBreakdown::requests).reversed()
                .thenComparing(TrafficServiceBreakdown::serviceId));
        return List.copyOf(services);
    }

    private static final class ServiceAccumulator {
        private final String serviceId;
        private long requests;
        private long status5xx;
        private double latencyWeighted;
        private Double latencyMax;

        private ServiceAccumulator(String serviceId) {
            this.serviceId = serviceId;
        }

        private void add(TrafficMinuteBucket bucket) {
            requests += bucket.requests();
            status5xx += bucket.status5xx();
            latencyWeighted += bucket.latencyAvgMs() * bucket.requests();
            if (bucket.latencyMaxMs() != null) {
                latencyMax = latencyMax == null
                        ? bucket.latencyMaxMs()
                        : Math.max(latencyMax, bucket.latencyMaxMs());
            }
        }

        private TrafficServiceBreakdown toBreakdown() {
            double latencyAvg = requests == 0 ? 0.0 : latencyWeighted / requests;
            return new TrafficServiceBreakdown(serviceId, requests, status5xx, latencyAvg, latencyMax);
        }
    }
}
