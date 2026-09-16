package com.ivan.nexus.application.project;

import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.Project;
import com.ivan.nexus.domain.project.ProjectGrouping;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DiscoverProjects {
    private final ContainerInventory inventory;

    public DiscoverProjects(ContainerInventory inventory) {
        this.inventory = inventory;
    }

    public List<Project> execute() {
        return groupByProject().entrySet().stream()
                .map(entry -> toProject(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Project::id))
                .toList();
    }

    Map<String, List<ContainerSnapshot>> groupByProject() {
        Map<String, List<ContainerSnapshot>> grouped = new LinkedHashMap<>();
        for (ContainerSnapshot snapshot : inventory.listAll()) {
            String projectId = ProjectGrouping.projectId(snapshot.name(), snapshot.labels());
            grouped.computeIfAbsent(projectId, key -> new ArrayList<>()).add(snapshot);
        }
        return grouped;
    }

    static Project toProject(String id, List<ContainerSnapshot> containers) {
        int runningCount = 0;
        int unhealthyCount = 0;
        for (ContainerSnapshot container : containers) {
            if (isRunning(container)) {
                runningCount++;
            }
            if (isUnhealthy(container)) {
                unhealthyCount++;
            }
        }
        return new Project(id, id, status(runningCount, containers.size(), unhealthyCount), runningCount, containers.size());
    }

    private static String status(int runningCount, int totalCount, int unhealthyCount) {
        if (runningCount == 0) {
            return "DOWN";
        }
        if (runningCount == totalCount && unhealthyCount == 0) {
            return "HEALTHY";
        }
        return "DEGRADED";
    }

    static boolean isRunning(ContainerSnapshot container) {
        return container.state() != null && container.state().equalsIgnoreCase("running");
    }

    static boolean isUnhealthy(ContainerSnapshot container) {
        return container.health() != null && container.health().equalsIgnoreCase("unhealthy");
    }
}
