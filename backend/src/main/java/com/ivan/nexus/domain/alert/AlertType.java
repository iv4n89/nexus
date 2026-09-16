package com.ivan.nexus.domain.alert;

public enum AlertType {
    CONTAINER_STOPPED,
    RESTART_SPIKE,
    HIGH_MEMORY,
    DISK,
    ERROR_RATE,
    DOCKER_HEALTH,
    HTTP_HEALTH
}
