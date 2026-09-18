package com.ivan.nexus.domain.manifest;

import java.util.List;

public record ProjectManifest(
        ProjectBlock project,
        List<String> services,
        CommandBlock deployment,
        CommandBlock rollback,
        HealthBlock health,
        AlertsBlock alerts,
        BackupBlock backup
) {
    /** Convenience for callers that omit the optional backup block. */
    public ProjectManifest(
            ProjectBlock project,
            List<String> services,
            CommandBlock deployment,
            CommandBlock rollback,
            HealthBlock health,
            AlertsBlock alerts) {
        this(project, services, deployment, rollback, health, alerts, null);
    }

    public record ProjectBlock(String id, String name, String description, String workingDirectory) {}

    public record CommandBlock(String command) {}

    /**
     * If {@code url} is present, deploy succeeds only when that endpoint returns 2xx.
     * If absent, deploy success is process exit 0 only.
     */
    public record HealthBlock(String url, Integer timeoutSeconds) {}

    public record AlertsBlock(Integer errorRatePerMinute, Integer restartCount, Integer memoryPercent) {}

    /**
     * Optional backup declarations from {@code nexus.yml}.
     * Only volumes listed here are included in volume backups.
     */
    public record BackupBlock(List<String> volumes) {}
}
