package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RunBackup {
    private final BackupProvider provider;
    private final VolumeBackupProvider volumes;
    private final BackupStorage storage;
    private final BackupStore store;
    private final ManifestCatalog manifests;
    private final RecordActivity recordActivity;

    public RunBackup(
            BackupProvider provider,
            VolumeBackupProvider volumes,
            BackupStorage storage,
            BackupStore store,
            ManifestCatalog manifests,
            RecordActivity recordActivity) {
        this.provider = provider;
        this.volumes = volumes;
        this.storage = storage;
        this.store = store;
        this.manifests = manifests;
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
                "backup in progress",
                false,
                false);
        store.save(running);

        try {
            List<String> parts = new ArrayList<>();
            boolean includesDb = false;
            boolean includesVolumes = false;
            String primaryUri = null;

            PostgresDumpResult dump = provider.dumpPostgres(projectId);
            String dbUri = storage.store(projectId, pathFromUri(dump.artifactUri()));
            primaryUri = dbUri;
            includesDb = true;
            parts.add(dump.summary());

            List<String> declaredVolumes = declaredVolumes(projectId);
            if (!declaredVolumes.isEmpty()) {
                VolumeBackupResult volumeResult = volumes.backupVolumes(projectId, declaredVolumes);
                String volumeUri = storage.store(projectId, pathFromUri(volumeResult.artifactUri()));
                if (primaryUri == null) {
                    primaryUri = volumeUri;
                } else {
                    parts.add("volumes@" + volumeUri);
                }
                includesVolumes = true;
                parts.add(volumeResult.summary());
            }

            Backup success = new Backup(
                    running.id(),
                    projectId,
                    BackupStatus.SUCCESS,
                    kind,
                    started,
                    Instant.now(),
                    primaryUri,
                    String.join("; ", parts),
                    includesDb,
                    includesVolumes);
            Backup saved = store.save(success);
            recordActivity.execute(
                    ActivityType.BACKUP_COMPLETED,
                    projectId,
                    null,
                    "backup completed",
                    Map.of(
                            "backupId", saved.id().toString(),
                            "artifactUri", primaryUri == null ? "" : primaryUri,
                            "includesDb", includesDb,
                            "includesVolumes", includesVolumes,
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
                    false,
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

    private List<String> declaredVolumes(String projectId) {
        try {
            ProjectManifest.BackupBlock backup = manifests.loadRequired(projectId).manifest().backup();
            if (backup == null || backup.volumes() == null || backup.volumes().isEmpty()) {
                return List.of();
            }
            return backup.volumes().stream().map(String::trim).filter(v -> !v.isBlank()).toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String pathFromUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return uri;
        }
        if (uri.startsWith("file:")) {
            return java.nio.file.Path.of(java.net.URI.create(uri)).toString();
        }
        return uri;
    }

    private static String truncate(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }
}
