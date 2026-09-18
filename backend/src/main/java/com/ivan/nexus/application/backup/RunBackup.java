package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RunBackup {
    private final BackupProvider provider;
    private final BackupStore store;
    private final RecordActivity recordActivity;

    public RunBackup(BackupProvider provider, BackupStore store, RecordActivity recordActivity) {
        this.provider = provider;
        this.store = store;
        this.recordActivity = recordActivity;
    }

    @Transactional
    public Backup execute(String projectId, BackupKind kind) {
        Instant started = Instant.now();
        Backup running = new Backup(
                UUID.randomUUID(),
                projectId,
                BackupStatus.RUNNING,
                kind,
                started,
                null,
                null,
                "dump in progress",
                true,
                false);
        store.save(running);

        try {
            PostgresDumpResult dump = provider.dumpPostgres(projectId);
            Backup success = new Backup(
                    running.id(),
                    projectId,
                    BackupStatus.SUCCESS,
                    kind,
                    started,
                    Instant.now(),
                    dump.artifactUri(),
                    dump.summary(),
                    true,
                    false);
            Backup saved = store.save(success);
            recordActivity.execute(
                    ActivityType.BACKUP_COMPLETED,
                    projectId,
                    null,
                    "backup completed",
                    Map.of(
                            "backupId", saved.id().toString(),
                            "artifactUri", dump.artifactUri(),
                            "sizeBytes", dump.sizeBytes(),
                            "kind", kind.name()));
            return saved;
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            Backup failed = new Backup(
                    running.id(),
                    projectId,
                    BackupStatus.FAILED,
                    kind,
                    started,
                    Instant.now(),
                    null,
                    truncate(message),
                    true,
                    false);
            Backup saved = store.save(failed);
            recordActivity.execute(
                    ActivityType.BACKUP_FAILED,
                    projectId,
                    null,
                    "backup failed",
                    Map.of(
                            "backupId", saved.id().toString(),
                            "kind", kind.name(),
                            "detail", truncate(message)));
            return saved;
        }
    }

    private static String truncate(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }
}
