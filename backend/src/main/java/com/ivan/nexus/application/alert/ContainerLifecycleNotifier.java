package com.ivan.nexus.application.alert;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.ProjectGrouping;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ContainerLifecycleNotifier {
    private final RecordActivity recordActivity;

    public ContainerLifecycleNotifier(RecordActivity recordActivity) {
        this.recordActivity = recordActivity;
    }

    void emitContainerActivity(
            Map<String, List<ContainerSnapshot>> grouped,
            Map<String, ContainerSnapshot> previousSnapshots,
            boolean primed) {
        for (var entry : grouped.entrySet()) {
            String projectId = entry.getKey();
            for (ContainerSnapshot current : entry.getValue()) {
                String serviceId = ProjectGrouping.serviceId(current.name(), current.labels());
                ContainerSnapshot previous = previousSnapshots.get(current.id());
                if (previous == null) {
                    if (primed && isRunning(current.state())) {
                        recordContainer(ActivityType.CONTAINER_STARTED, projectId, serviceId, current, "started");
                    }
                    continue;
                }
                if (current.restartCount() > previous.restartCount()) {
                    recordContainer(ActivityType.CONTAINER_RESTARTED, projectId, serviceId, current, "restarted");
                    continue;
                }
                boolean wasRunning = isRunning(previous.state());
                boolean nowRunning = isRunning(current.state());
                if (wasRunning && !nowRunning) {
                    recordContainer(ActivityType.CONTAINER_STOPPED, projectId, serviceId, current, "stopped");
                } else if (!wasRunning && nowRunning) {
                    recordContainer(ActivityType.CONTAINER_STARTED, projectId, serviceId, current, "started");
                }
            }
        }
    }

    void recordContainer(
            ActivityType type,
            String projectId,
            String serviceId,
            ContainerSnapshot container,
            String message) {
        recordActivity.execute(
                type,
                projectId,
                serviceId,
                message,
                Map.of("containerId", container.id(), "restartCount", container.restartCount()));
    }

    static boolean isRunning(String state) {
        return state != null && state.equalsIgnoreCase("running");
    }
}
