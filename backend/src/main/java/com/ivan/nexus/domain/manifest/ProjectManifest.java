package com.ivan.nexus.domain.manifest;

import java.util.List;

public record ProjectManifest(
        ProjectBlock project,
        List<String> services,
        CommandBlock deployment,
        CommandBlock rollback,
        HealthBlock health,
        AlertsBlock alerts
) {
    public record ProjectBlock(String id, String name, String description, String workingDirectory) {}

    public record CommandBlock(String command) {}

    /**
     * If {@code url} is present, deploy succeeds only when that endpoint returns 2xx.
     * If absent, deploy success is process exit 0 only.
     */
    public record HealthBlock(String url, Integer timeoutSeconds) {}

    public record AlertsBlock(Integer errorRatePerMinute, Integer restartCount, Integer memoryPercent) {}
}
