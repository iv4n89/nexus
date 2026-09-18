package com.ivan.nexus.domain.backup;

import java.time.Instant;
import java.util.UUID;

public record Backup(
        UUID id,
        String projectId,
        BackupStatus status,
        BackupKind kind,
        Instant createdAt,
        Instant finishedAt,
        String artifactUri,
        String summary,
        boolean includesDb,
        boolean includesVolumes) {
}
