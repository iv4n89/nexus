package com.ivan.nexus.domain.traffic;

import java.time.Instant;
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
        double latencyAvg,
        Double latencyP95) {

    public TrafficHourlyBucket ingest(int status, long bytes, long latencyMs) {
        long nextRequests = requests + 1;
        long nextBytesOut = bytesOut + Math.max(0, bytes);
        long next2xx = status2xx + (status >= 200 && status < 300 ? 1 : 0);
        long next3xx = status3xx + (status >= 300 && status < 400 ? 1 : 0);
        long next4xx = status4xx + (status >= 400 && status < 500 ? 1 : 0);
        long next5xx = status5xx + (status >= 500 && status < 600 ? 1 : 0);
        double nextAvg = ((latencyAvg * requests) + latencyMs) / nextRequests;
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
                latencyP95);
    }

    public static TrafficHourlyBucket empty(
            UUID id, String projectId, String domainId, Instant bucketStart) {
        return new TrafficHourlyBucket(
                id, projectId, normalizeDomainId(domainId), bucketStart, 0, 0, 0, 0, 0, 0, 0, 0.0, null);
    }

    public static String normalizeDomainId(String domainId) {
        return domainId == null ? "" : domainId;
    }
}
