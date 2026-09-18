package com.ivan.nexus.domain.traffic;

public record TrafficServiceBreakdown(
        String serviceId,
        long requests,
        long status5xx,
        double latencyAvgMs,
        Double latencyMaxMs) {}
