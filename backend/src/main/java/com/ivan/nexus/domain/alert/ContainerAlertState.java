package com.ivan.nexus.domain.alert;

public record ContainerAlertState(
        String containerId,
        String projectId,
        String serviceId,
        String state,
        String health,
        int restartCount,
        Double memoryPercent,
        int memoryThreshold) {
}
