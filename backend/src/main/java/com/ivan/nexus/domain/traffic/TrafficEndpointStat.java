package com.ivan.nexus.domain.traffic;

/**
 * Aggregated request stats for a single HTTP path within an hourly bucket.
 */
public record TrafficEndpointStat(String path, long requests, double latencyAvgMs) {

    public TrafficEndpointStat {
        path = path == null || path.isBlank() ? "/" : path;
        if (requests < 0) {
            throw new IllegalArgumentException("requests must be >= 0");
        }
    }

    public TrafficEndpointStat ingest(long latencyMs) {
        long nextRequests = requests + 1;
        double nextAvg = ((latencyAvgMs * requests) + latencyMs) / nextRequests;
        return new TrafficEndpointStat(path, nextRequests, nextAvg);
    }
}
