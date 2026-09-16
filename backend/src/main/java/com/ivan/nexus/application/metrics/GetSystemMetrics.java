package com.ivan.nexus.application.metrics;

import com.ivan.nexus.domain.metrics.SystemMetrics;
import org.springframework.stereotype.Service;

@Service
public class GetSystemMetrics {
    private final SystemMetricsProvider provider;

    public GetSystemMetrics(SystemMetricsProvider provider) {
        this.provider = provider;
    }

    public SystemMetrics execute() {
        return provider.get();
    }
}
