package com.ivan.nexus.domain.traffic;

public record TrafficProjectRanking(
        String projectId,
        long requests,
        long status5xx,
        double latencyAvgMs) {}
