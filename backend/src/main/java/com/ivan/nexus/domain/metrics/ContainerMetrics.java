package com.ivan.nexus.domain.metrics;

public record ContainerMetrics(
        String containerId,
        double cpuPercent,
        long memoryUsedBytes,
        long memoryLimitBytes,
        long rxBytes,
        long txBytes) {
}
