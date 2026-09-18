package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupException;
import com.ivan.nexus.application.backup.VolumeBackupProvider;
import com.ivan.nexus.application.backup.VolumeBackupResult;

import java.util.List;

public class NoOpVolumeBackupProvider implements VolumeBackupProvider {
    @Override
    public VolumeBackupResult backupVolumes(String projectId, List<String> volumeNames) {
        if (volumeNames == null || volumeNames.isEmpty()) {
            return new VolumeBackupResult(null, 0L, List.of(), "no volumes declared");
        }
        throw new BackupException("Volume backups are disabled (nexus.backup.enabled=false)");
    }
}
