package com.ivan.nexus.domain.traffic;

import java.time.Instant;
import java.util.UUID;

public record TrafficMinuteBucket(
        UUID id,
        String projectId,
        String serviceId,
        String host,
        Instant bucketStart,
        long requests,
        long bytesIn,
        long bytesOut,
        long status2xx,
        long status3xx,
        long status4xx,
        long status5xx,
        double latencyAvgMs,
        Double latencyMaxMs) {

    public static final String UNMAPPED_PROJECT = "_unmapped";
    public static final String UNKNOWN_SERVICE = "_unknown";

    public TrafficMinuteBucket ingest(int status, long bytes, long latencyMs) {
        long nextRequests = requests + 1;
        long nextBytesOut = bytesOut + Math.max(0, bytes);
        long next2xx = status2xx + (status >= 200 && status < 300 ? 1 : 0);
        long next3xx = status3xx + (status >= 300 && status < 400 ? 1 : 0);
        long next4xx = status4xx + (status >= 400 && status < 500 ? 1 : 0);
        long next5xx = status5xx + (status >= 500 && status < 600 ? 1 : 0);
        double nextAvg = ((latencyAvgMs * requests) + latencyMs) / nextRequests;
        Double nextMax = latencyMaxMs == null
                ? (double) latencyMs
                : Math.max(latencyMaxMs, latencyMs);
        return new TrafficMinuteBucket(
                id,
                projectId,
                serviceId,
                host,
                bucketStart,
                nextRequests,
                bytesIn,
                nextBytesOut,
                next2xx,
                next3xx,
                next4xx,
                next5xx,
                nextAvg,
                nextMax);
    }

    public static TrafficMinuteBucket empty(
            UUID id, String projectId, String serviceId, String host, Instant bucketStart) {
        return new TrafficMinuteBucket(
                id,
                projectId,
                normalizeServiceId(serviceId),
                normalizeHost(host),
                bucketStart,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                null);
    }

    public static String normalizeServiceId(String serviceId) {
        return serviceId == null || serviceId.isBlank() ? "app" : serviceId.trim();
    }

    public static String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase();
    }
}
