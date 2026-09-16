package com.ivan.nexus.domain.metrics;

public record SystemMetrics(
        double cpuPercent,
        long memoryUsedBytes,
        long memoryTotalBytes,
        long diskUsedBytes,
        long diskTotalBytes,
        double loadAverage,
        long uptimeSeconds) {
}
