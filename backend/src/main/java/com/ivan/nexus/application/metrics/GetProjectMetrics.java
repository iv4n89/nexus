package com.ivan.nexus.application.metrics;

import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.metrics.ProjectMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GetProjectMetrics {
    private static final Logger log = LoggerFactory.getLogger(GetProjectMetrics.class);

    private final GetProject getProject;
    private final ContainerStatsProvider statsProvider;

    public GetProjectMetrics(GetProject getProject, ContainerStatsProvider statsProvider) {
        this.getProject = getProject;
        this.statsProvider = statsProvider;
    }

    public ProjectMetrics execute(String projectId) {
        GetProject.Result result = getProject.execute(projectId);
        double cpuPercent = 0;
        long memoryUsedBytes = 0;
        int restartCount = 0;
        for (ContainerSnapshot container : result.containers()) {
            if (!isRunning(container)) {
                continue;
            }
            restartCount += container.restartCount();
            try {
                ContainerMetrics stats = statsProvider.stats(container.id());
                cpuPercent += stats.cpuPercent();
                memoryUsedBytes += stats.memoryUsedBytes();
            } catch (RuntimeException ex) {
                log.warn("Skipping stats for container {} in project {}", container.id(), projectId, ex);
            }
        }
        return new ProjectMetrics(projectId, cpuPercent, memoryUsedBytes, restartCount);
    }

    private static boolean isRunning(ContainerSnapshot container) {
        return container.state() != null && container.state().equalsIgnoreCase("running");
    }
}
