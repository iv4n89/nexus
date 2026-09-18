package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupRestoreProvider;

import java.util.List;

public class NoOpBackupRestoreProvider implements BackupRestoreProvider {
    @Override
    public void restorePostgres(String projectId, String artifactUri) {
        // disabled
    }

    @Override
    public void restoreVolumes(String projectId, String artifactUri, List<String> volumeNames) {
        // disabled
    }
}
