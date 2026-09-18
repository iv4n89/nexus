package com.ivan.nexus.application.backup;

/**
 * Result of a successful Postgres dump written by {@link BackupProvider}.
 */
public record PostgresDumpResult(
        String artifactUri,
        long sizeBytes,
        String databaseName,
        String summary) {
}
