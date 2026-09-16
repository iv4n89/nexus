package com.ivan.nexus.domain.metrics;

public record ProjectMetrics(
        String projectId,
        double cpuPercent,
        long memoryUsedBytes,
        int restartCount) {
}
