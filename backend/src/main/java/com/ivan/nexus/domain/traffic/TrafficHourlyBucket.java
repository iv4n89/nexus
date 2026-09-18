package com.ivan.nexus.domain.traffic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record TrafficHourlyBucket(
        UUID id,
        String projectId,
        String domainId,
        Instant bucketStart,
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

    public static final int MAX_TOP_ENDPOINTS = 10;

    public TrafficHourlyBucket {
        topEndpoints = List.copyOf(topEndpoints == null ? List.of() : topEndpoints);
    }

    public TrafficHourlyBucket ingest(int status, long bytes, long latencyMs) {
        return ingest(status, bytes, latencyMs, null);
    }

    public TrafficHourlyBucket ingest(int status, long bytes, long latencyMs, String endpoint) {
        long nextRequests = requests + 1;
        long nextBytesOut = bytesOut + Math.max(0, bytes);
        long next2xx = status2xx + (status >= 200 && status < 300 ? 1 : 0);
        long next3xx = status3xx + (status >= 300 && status < 400 ? 1 : 0);
        long next4xx = status4xx + (status >= 400 && status < 500 ? 1 : 0);
        long next5xx = status5xx + (status >= 500 && status < 600 ? 1 : 0);
        double nextAvg = ((latencyAvgMs * requests) + latencyMs) / nextRequests;
        Double nextP95 = latencyP95Ms == null
                ? (double) latencyMs
                : Math.max(latencyP95Ms, latencyMs);
        List<TrafficEndpointStat> nextEndpoints = mergeEndpoint(topEndpoints, endpoint, latencyMs);
        return new TrafficHourlyBucket(
                id,
                projectId,
                domainId,
                bucketStart,
                nextRequests,
                bytesIn,
                nextBytesOut,
                next2xx,
                next3xx,
                next4xx,
                next5xx,
                nextAvg,
                nextP95,
                nextEndpoints);
    }

    public static TrafficHourlyBucket empty(
            UUID id, String projectId, String domainId, Instant bucketStart) {
        return new TrafficHourlyBucket(
                id,
                projectId,
                normalizeDomainId(domainId),
                bucketStart,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                null,
                List.of());
    }

    public static String normalizeDomainId(String domainId) {
        return domainId == null ? "" : domainId;
    }

    static List<TrafficEndpointStat> mergeEndpoint(
            List<TrafficEndpointStat> current, String endpoint, long latencyMs) {
        if (endpoint == null || endpoint.isBlank()) {
            return current;
        }
        String path = endpoint.trim();
        List<TrafficEndpointStat> merged = new ArrayList<>(current.size() + 1);
        boolean found = false;
        for (TrafficEndpointStat stat : current) {
            if (stat.path().equals(path)) {
                merged.add(stat.ingest(latencyMs));
                found = true;
            } else {
                merged.add(stat);
            }
        }
        if (!found) {
            merged.add(new TrafficEndpointStat(path, 1, latencyMs));
        }
        merged.sort(Comparator.comparingLong(TrafficEndpointStat::requests).reversed()
                .thenComparing(TrafficEndpointStat::path));
        if (merged.size() > MAX_TOP_ENDPOINTS) {
            return List.copyOf(merged.subList(0, MAX_TOP_ENDPOINTS));
        }
        return List.copyOf(merged);
    }
}
