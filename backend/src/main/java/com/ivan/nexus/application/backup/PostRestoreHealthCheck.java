package com.ivan.nexus.application.backup;

/**
 * Post-restore health verification hook (F7).
 */
public interface PostRestoreHealthCheck {
    /**
     * @return true when the project looks healthy after restore, or when no check is configured
     */
    boolean verify(String projectId);
}
