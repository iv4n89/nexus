package com.ivan.nexus.domain.backup;

public record BackupPolicy(
        String projectId,
        int dailyRetention,
        int weeklyRetention,
        int monthlyRetention,
        boolean enabled,
        String scheduleCron) {

    public static BackupPolicy defaults(String projectId) {
        return new BackupPolicy(projectId, 7, 4, 3, false, null);
    }
}
