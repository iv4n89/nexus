package com.ivan.nexus.application.backup;

/**
 * Restores a previously created backup artifact into the project.
 */
public interface BackupRestoreProvider {
    void restorePostgres(String projectId, String artifactUri);

    void restoreVolumes(String projectId, String artifactUri, java.util.List<String> volumeNames);
}
