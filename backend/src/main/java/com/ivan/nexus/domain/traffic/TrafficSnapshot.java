package com.ivan.nexus.domain.traffic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TrafficSnapshot(
        String projectId,
        Instant from,
        Instant to,
        long requests,
        long bytesIn,
        long bytesOut,
        long status2xx,
        long status3xx,
        long status4xx,
        long status5xx,
        double latencyAvgMs,
        Double latencyP95Ms,
        List<TrafficEndpointStat> topEndpoints) {

    public TrafficSnapshot {
        topEndpoints = List.copyOf(topEndpoints == null ? List.of() : topEndpoints);
    }

    public static TrafficSnapshot aggregate(
            String projectId, Instant from, Instant to, List<TrafficHourlyBucket> buckets) {
        long requests = 0;
        long bytesIn = 0;
        long bytesOut = 0;
        long status2xx = 0;
        long status3xx = 0;
        long status4xx = 0;
        long status5xx = 0;
        double latencyWeighted = 0;
        Double latencyP95 = null;
        Map<String, TrafficEndpointStat> endpoints = new HashMap<>();
        for (TrafficHourlyBucket bucket : buckets) {
            requests += bucket.requests();
            bytesIn += bucket.bytesIn();
            bytesOut += bucket.bytesOut();
            status2xx += bucket.status2xx();
            status3xx += bucket.status3xx();
            status4xx += bucket.status4xx();
            status5xx += bucket.status5xx();
            latencyWeighted += bucket.latencyAvgMs() * bucket.requests();
            if (bucket.latencyP95Ms() != null) {
                latencyP95 = latencyP95 == null
                        ? bucket.latencyP95Ms()
                        : Math.max(latencyP95, bucket.latencyP95Ms());
            }
            for (TrafficEndpointStat stat : bucket.topEndpoints()) {
                endpoints.merge(stat.path(), stat, TrafficSnapshot::mergeStats);
            }
        }
        double latencyAvg = requests == 0 ? 0.0 : latencyWeighted / requests;
        List<TrafficEndpointStat> top = new ArrayList<>(endpoints.values());
        top.sort(Comparator.comparingLong(TrafficEndpointStat::requests).reversed()
                .thenComparing(TrafficEndpointStat::path));
        if (top.size() > TrafficHourlyBucket.MAX_TOP_ENDPOINTS) {
            top = top.subList(0, TrafficHourlyBucket.MAX_TOP_ENDPOINTS);
        }
        return new TrafficSnapshot(
                projectId,
                from,
                to,
                requests,
                bytesIn,
                bytesOut,
                status2xx,
                status3xx,
                status4xx,
                status5xx,
                latencyAvg,
                latencyP95,
                top);
    }

    public static TrafficSnapshot aggregateMinutes(
            String projectId, Instant from, Instant to, List<TrafficMinuteBucket> buckets) {
        long requests = 0;
        long bytesIn = 0;
        long bytesOut = 0;
        long status2xx = 0;
        long status3xx = 0;
        long status4xx = 0;
        long status5xx = 0;
        double latencyWeighted = 0;
        Double latencyP95 = null;
        for (TrafficMinuteBucket bucket : buckets) {
            requests += bucket.requests();
            bytesIn += bucket.bytesIn();
            bytesOut += bucket.bytesOut();
            status2xx += bucket.status2xx();
            status3xx += bucket.status3xx();
            status4xx += bucket.status4xx();
            status5xx += bucket.status5xx();
            latencyWeighted += bucket.latencyAvgMs() * bucket.requests();
            if (bucket.latencyMaxMs() != null) {
                latencyP95 = latencyP95 == null
                        ? bucket.latencyMaxMs()
                        : Math.max(latencyP95, bucket.latencyMaxMs());
            }
        }
        double latencyAvg = requests == 0 ? 0.0 : latencyWeighted / requests;
        return new TrafficSnapshot(
                projectId,
                from,
                to,
                requests,
                bytesIn,
                bytesOut,
                status2xx,
                status3xx,
                status4xx,
                status5xx,
                latencyAvg,
                latencyP95,
                List.of());
    }

    private static TrafficEndpointStat mergeStats(TrafficEndpointStat a, TrafficEndpointStat b) {
        long total = a.requests() + b.requests();
        double avg = total == 0
                ? 0.0
                : ((a.latencyAvgMs() * a.requests()) + (b.latencyAvgMs() * b.requests())) / total;
        return new TrafficEndpointStat(a.path(), total, avg);
    }
}
