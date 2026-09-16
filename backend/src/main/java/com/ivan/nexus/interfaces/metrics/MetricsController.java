package com.ivan.nexus.interfaces.metrics;

import com.ivan.nexus.application.metrics.GetProjectMetrics;
import com.ivan.nexus.application.metrics.GetSystemMetrics;
import com.ivan.nexus.domain.metrics.ProjectMetrics;
import com.ivan.nexus.domain.metrics.SystemMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {
    private final GetSystemMetrics getSystemMetrics;
    private final GetProjectMetrics getProjectMetrics;

    public MetricsController(GetSystemMetrics getSystemMetrics, GetProjectMetrics getProjectMetrics) {
        this.getSystemMetrics = getSystemMetrics;
        this.getProjectMetrics = getProjectMetrics;
    }

    @GetMapping("/api/metrics/system")
    public SystemMetrics system() {
        return getSystemMetrics.execute();
    }

    @GetMapping("/api/projects/{id}/metrics")
    public ProjectMetrics project(@PathVariable String id) {
        return getProjectMetrics.execute(id);
    }
}
