package com.ivan.nexus.application.backup;

import java.util.List;

/**
 * Outbound port for backing up Docker volumes declared in {@code nexus.yml}.
 */
public interface VolumeBackupProvider {
    /**
     * Archives the given Docker volumes for a project.
     *
     * @return metadata for the written archive (may be empty when {@code volumeNames} is empty)
     */
    VolumeBackupResult backupVolumes(String projectId, List<String> volumeNames);
}
