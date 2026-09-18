package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RestoreBackup {
    private final BackupStore store;
    private final RunBackup runBackup;
    private final BackupRestoreProvider restoreProvider;
    private final PostRestoreHealthCheck healthCheck;
    private final ManifestCatalog manifests;
    private final RecordActivity recordActivity;
    private final RecordAudit recordAudit;
    private final UserDirectory users;

    public RestoreBackup(
            BackupStore store,
            RunBackup runBackup,
            BackupRestoreProvider restoreProvider,
            PostRestoreHealthCheck healthCheck,
            ManifestCatalog manifests,
            RecordActivity recordActivity,
            RecordAudit recordAudit,
            UserDirectory users) {
        this.store = store;
        this.runBackup = runBackup;
        this.restoreProvider = restoreProvider;
        this.healthCheck = healthCheck;
        this.manifests = manifests;
        this.recordActivity = recordActivity;
        this.recordAudit = recordAudit;
        this.users = users;
    }

    @Transactional
    public RestoreResult execute(
            String projectId,
            UUID backupId,
            boolean confirm,
            String username,
            String ip) {
        if (!confirm) {
            throw new DomainException(NexusErrorCode.CONFIRMATION_REQUIRED, "Confirmation required");
        }

        Backup target = store.findById(backupId)
                .orElseThrow(() -> new DomainException(NexusErrorCode.BACKUP_NOT_FOUND, "Backup not found"));
        if (!projectId.equals(target.projectId())) {
            throw new DomainException(NexusErrorCode.BACKUP_NOT_FOUND, "Backup not found");
        }
        if (target.status() != BackupStatus.SUCCESS || target.artifactUri() == null) {
            throw new DomainException(NexusErrorCode.BACKUP_RESTORE_FAILED, "Backup is not restorable");
        }

        Backup safety = runBackup.execute(projectId, BackupKind.SAFETY);

        try {
            if (target.includesDb()) {
                restoreProvider.restorePostgres(projectId, target.artifactUri());
            }
            List<String> volumes = declaredVolumes(projectId);
            if (target.includesVolumes() && !volumes.isEmpty()) {
                String volumeUri = volumeArtifactUri(target);
                restoreProvider.restoreVolumes(projectId, volumeUri, volumes);
            }
        } catch (RuntimeException ex) {
            throw new DomainException(
                    NexusErrorCode.BACKUP_RESTORE_FAILED,
                    ex.getMessage() == null ? "Restore failed" : ex.getMessage());
        }

        boolean healthy = healthCheck.verify(projectId);
        UUID userId = users.findIdByUsername(username).orElse(null);
        recordAudit.execute(
                userId,
                AuditAction.BACKUP_RESTORE,
                projectId,
                null,
                ip,
                Map.of(
                        "backupId", backupId.toString(),
                        "safetyBackupId", safety.id().toString(),
                        "healthy", healthy));
        recordActivity.execute(
                ActivityType.BACKUP_RESTORED,
                projectId,
                null,
                "backup restored",
                Map.of(
                        "backupId", backupId.toString(),
                        "safetyBackupId", safety.id().toString(),
                        "healthy", healthy));

        return new RestoreResult(target, safety, healthy);
    }

    private List<String> declaredVolumes(String projectId) {
        try {
            ProjectManifest.BackupBlock backup = manifests.loadRequired(projectId).manifest().backup();
            if (backup == null || backup.volumes() == null) {
                return List.of();
            }
            return backup.volumes().stream().map(String::trim).filter(v -> !v.isBlank()).toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String volumeArtifactUri(Backup target) {
        String summary = target.summary() == null ? "" : target.summary();
        int marker = summary.indexOf("volumes@");
        if (marker >= 0) {
            String rest = summary.substring(marker + "volumes@".length());
            int end = rest.indexOf(';');
            return end >= 0 ? rest.substring(0, end).trim() : rest.trim();
        }
        return target.artifactUri();
    }

    public record RestoreResult(Backup restored, Backup safetyBackup, boolean healthy) {
    }
}
