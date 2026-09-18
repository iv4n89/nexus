package com.ivan.nexus.domain.traffic;

import java.time.Instant;
import java.util.UUID;

/**
 * Before/after traffic comparison around a deployment window.
 */
public record DeployTrafficDelta(
        String projectId,
        UUID deploymentId,
        Instant deployedAt,
        Instant beforeFrom,
        Instant beforeTo,
        Instant afterFrom,
        Instant afterTo,
        long beforeRequests,
        long afterRequests,
        long beforeStatus5xx,
        long afterStatus5xx,
        Double beforeLatencyP95Ms,
        Double afterLatencyP95Ms,
        double status5xxDeltaRate,
        Double latencyP95DeltaMs,
        double trafficDeltaPercent) {

    public static DeployTrafficDelta of(
            String projectId,
            UUID deploymentId,
            Instant deployedAt,
            Instant beforeFrom,
            Instant beforeTo,
            Instant afterFrom,
            Instant afterTo,
            TrafficSnapshot before,
            TrafficSnapshot after) {
        double beforeRate = rate(before.status5xx(), before.requests());
        double afterRate = rate(after.status5xx(), after.requests());
        Double latencyDelta = null;
        if (before.latencyP95Ms() != null && after.latencyP95Ms() != null) {
            latencyDelta = after.latencyP95Ms() - before.latencyP95Ms();
        } else if (after.latencyP95Ms() != null) {
            latencyDelta = after.latencyP95Ms();
        }
        double trafficDelta = before.requests() == 0
                ? (after.requests() == 0 ? 0.0 : 100.0)
                : ((after.requests() - before.requests()) * 100.0) / before.requests();
        return new DeployTrafficDelta(
                projectId,
                deploymentId,
                deployedAt,
                beforeFrom,
                beforeTo,
                afterFrom,
                afterTo,
                before.requests(),
                after.requests(),
                before.status5xx(),
                after.status5xx(),
                before.latencyP95Ms(),
                after.latencyP95Ms(),
                afterRate - beforeRate,
                latencyDelta,
                trafficDelta);
    }

    private static double rate(long count, long total) {
        return total == 0 ? 0.0 : (count * 100.0) / total;
    }
}
