package com.ivan.nexus.domain.traffic;

import java.time.Instant;
import java.util.List;

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
        double latencyAvg,
        Double latencyP95) {

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
        for (TrafficHourlyBucket bucket : buckets) {
            requests += bucket.requests();
            bytesIn += bucket.bytesIn();
            bytesOut += bucket.bytesOut();
            status2xx += bucket.status2xx();
            status3xx += bucket.status3xx();
            status4xx += bucket.status4xx();
            status5xx += bucket.status5xx();
            latencyWeighted += bucket.latencyAvg() * bucket.requests();
            if (bucket.latencyP95() != null) {
                latencyP95 = latencyP95 == null
                        ? bucket.latencyP95()
                        : Math.max(latencyP95, bucket.latencyP95());
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
                latencyP95);
    }
}
