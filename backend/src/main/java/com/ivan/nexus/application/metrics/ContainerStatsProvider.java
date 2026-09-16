package com.ivan.nexus.application.metrics;

import com.ivan.nexus.domain.metrics.ContainerMetrics;

public interface ContainerStatsProvider {
    ContainerMetrics stats(String containerId);
}
