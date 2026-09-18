package com.ivan.nexus.application.backup;

import java.util.List;

/**
 * Outbound port for durable backup artifact storage (local disk or remote).
 */
public interface BackupStorage {
    /**
     * Ensures the artifact is available under the configured storage target.
     * For local storage this is typically a no-op when the file already exists at {@code localPath}.
     *
     * @return stable URI for the stored artifact
     */
    String store(String projectId, String localPath);

    boolean exists(String artifactUri);

    void delete(String artifactUri);
}
