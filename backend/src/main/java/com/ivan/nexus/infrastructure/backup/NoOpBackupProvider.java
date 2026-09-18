package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupException;
import com.ivan.nexus.application.backup.BackupProvider;
import com.ivan.nexus.application.backup.PostgresDumpResult;

/**
 * Used when {@code nexus.backup.enabled} is false.
 */
public class NoOpBackupProvider implements BackupProvider {
    @Override
    public PostgresDumpResult dumpPostgres(String projectId) {
        throw new BackupException("Postgres backups are disabled (nexus.backup.enabled=false)");
    }
}
