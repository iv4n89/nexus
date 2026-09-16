package com.ivan.nexus.application.metrics;

import com.ivan.nexus.domain.metrics.SystemMetrics;

public interface SystemMetricsProvider {
    SystemMetrics get();
}
