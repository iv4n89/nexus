package com.ivan.nexus.application.backup;

/**
 * Outbound port for creating project database dumps.
 */
public interface BackupProvider {
    /**
     * Dumps the first reachable Postgres instance for the project to durable storage.
     *
     * @return metadata for the written artifact
     * @throws BackupException when the dump cannot be produced
     */
    PostgresDumpResult dumpPostgres(String projectId);
}
