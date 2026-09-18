package com.ivan.nexus.interfaces.backup;

import com.ivan.nexus.application.backup.GetBackupPolicy;
import com.ivan.nexus.application.backup.ListBackups;
import com.ivan.nexus.application.backup.RestoreBackup;
import com.ivan.nexus.application.backup.UpsertBackupPolicy;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.backup.BackupStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{id}")
public class BackupController {
    private final GetBackupPolicy getBackupPolicy;
    private final UpsertBackupPolicy upsertBackupPolicy;
    private final ListBackups listBackups;
    private final RestoreBackup restoreBackup;

    public BackupController(
            GetBackupPolicy getBackupPolicy,
            UpsertBackupPolicy upsertBackupPolicy,
            ListBackups listBackups,
            RestoreBackup restoreBackup) {
        this.getBackupPolicy = getBackupPolicy;
        this.upsertBackupPolicy = upsertBackupPolicy;
        this.listBackups = listBackups;
        this.restoreBackup = restoreBackup;
    }

    @GetMapping("/backup-policy")
    public BackupPolicyResponse getPolicy(@PathVariable String id) {
        return BackupPolicyResponse.from(getBackupPolicy.execute(id));
    }

    @PutMapping("/backup-policy")
    public BackupPolicyResponse putPolicy(@PathVariable String id, @RequestBody BackupPolicyRequest request) {
        BackupPolicy updated = upsertBackupPolicy.execute(new BackupPolicy(
                id,
                request.dailyRetention(),
                request.weeklyRetention(),
                request.monthlyRetention(),
                request.enabled(),
                request.scheduleCron()));
        return BackupPolicyResponse.from(updated);
    }

    @GetMapping("/backups")
    public List<BackupResponse> backups(@PathVariable String id) {
        return listBackups.execute(id).stream().map(BackupResponse::from).toList();
    }

    @PostMapping("/backups/{backupId}/restore")
    public RestoreResponse restore(
            @PathVariable String id,
            @PathVariable UUID backupId,
            @RequestBody RestoreRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        RestoreBackup.RestoreResult result = restoreBackup.execute(
                id,
                backupId,
                request != null && request.confirm(),
                authentication.getName(),
                httpRequest.getRemoteAddr());
        return RestoreResponse.from(result);
    }

    public record RestoreRequest(boolean confirm) {
    }

    public record RestoreResponse(
            UUID backupId,
            UUID safetyBackupId,
            boolean healthy) {
        static RestoreResponse from(RestoreBackup.RestoreResult result) {
            return new RestoreResponse(
                    result.restored().id(),
                    result.safetyBackup().id(),
                    result.healthy());
        }
    }

    public record BackupPolicyRequest(
            int dailyRetention,
            int weeklyRetention,
            int monthlyRetention,
            boolean enabled,
            String scheduleCron) {
    }

    public record BackupPolicyResponse(
            String projectId,
            int dailyRetention,
            int weeklyRetention,
            int monthlyRetention,
            boolean enabled,
            String scheduleCron) {
        static BackupPolicyResponse from(BackupPolicy policy) {
            return new BackupPolicyResponse(
                    policy.projectId(),
                    policy.dailyRetention(),
                    policy.weeklyRetention(),
                    policy.monthlyRetention(),
                    policy.enabled(),
                    policy.scheduleCron());
        }
    }

    public record BackupResponse(
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
        static BackupResponse from(Backup backup) {
            return new BackupResponse(
                    backup.id(),
                    backup.projectId(),
                    backup.status(),
                    backup.kind(),
                    backup.createdAt(),
                    backup.finishedAt(),
                    backup.artifactUri(),
                    backup.summary(),
                    backup.includesDb(),
                    backup.includesVolumes());
        }
    }
}
