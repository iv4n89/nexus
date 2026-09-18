package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.backup.Backup;
import com.ivan.nexus.domain.backup.BackupPolicy;
import com.ivan.nexus.domain.backup.BackupStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enforces per-project backup retention and raises alerts for failed/stale backups (F5).
 */
@Service
@ConditionalOnProperty(name = "nexus.backup.scheduler-enabled", havingValue = "true")
public class EnforceBackupRetention {
    private static final Logger log = LoggerFactory.getLogger(EnforceBackupRetention.class);
    private static final Duration STALE_AFTER = Duration.ofHours(36);

    private final ManifestCatalog manifests;
    private final BackupPolicyStore policies;
    private final BackupStore store;
    private final BackupStorage storage;
    private final AlertStore alerts;
    private final AlertRuleStore rules;
    private final Clock clock;

    public EnforceBackupRetention(
            ManifestCatalog manifests,
            BackupPolicyStore policies,
            BackupStore store,
            BackupStorage storage,
            AlertStore alerts,
            AlertRuleStore rules,
            Clock clock) {
        this.manifests = manifests;
        this.policies = policies;
        this.store = store;
        this.storage = storage;
        this.alerts = alerts;
        this.rules = rules;
        this.clock = clock;
    }

    @Scheduled(cron = "${nexus.backup.retention-cron:0 30 3 * * *}")
    @Transactional
    public void execute() {
        Instant now = clock.instant();
        for (String projectId : manifests.discoverProjectIds()) {
            try {
                enforceProject(projectId, now);
            } catch (RuntimeException ex) {
                log.warn("Backup retention failed for {}: {}", projectId, ex.getMessage());
            }
        }
    }

    void enforceProject(String projectId, Instant now) {
        BackupPolicy policy = policies.findByProjectId(projectId).orElse(BackupPolicy.defaults(projectId));
        List<Backup> backups = store.findByProjectIdNewestFirst(projectId);

        alertFailed(projectId, backups, now);
        if (policy.enabled()) {
            alertStale(projectId, backups, now);
        }

        int keep = Math.max(policy.dailyRetention(), 1);
        List<Backup> successful = backups.stream()
                .filter(b -> b.status() == BackupStatus.SUCCESS)
                .sorted(Comparator.comparing(Backup::createdAt).reversed())
                .toList();
        if (successful.size() <= keep) {
            return;
        }
        for (Backup stale : successful.subList(keep, successful.size())) {
            if (stale.artifactUri() != null) {
                try {
                    storage.delete(stale.artifactUri());
                } catch (RuntimeException ex) {
                    log.debug("Failed to delete backup artifact {}: {}", stale.artifactUri(), ex.getMessage());
                }
            }
            store.delete(stale.id());
        }
    }

    private void alertFailed(String projectId, List<Backup> backups, Instant now) {
        Optional<Backup> latestFailed = backups.stream()
                .filter(b -> b.status() == BackupStatus.FAILED)
                .findFirst();
        if (latestFailed.isEmpty()) {
            alerts.resolve(new AlertKey(AlertType.BACKUP_FAILED, projectId, null), now);
            return;
        }
        Backup failed = latestFailed.get();
        Optional<Backup> newerSuccess = backups.stream()
                .filter(b -> b.status() == BackupStatus.SUCCESS)
                .filter(b -> b.createdAt().isAfter(failed.createdAt()))
                .findFirst();
        if (newerSuccess.isPresent()) {
            alerts.resolve(new AlertKey(AlertType.BACKUP_FAILED, projectId, null), now);
            return;
        }
        openAlert(AlertType.BACKUP_FAILED, projectId, "Latest backup failed: " + failed.summary(), now);
    }

    private void alertStale(String projectId, List<Backup> backups, Instant now) {
        Instant cutoff = now.minus(STALE_AFTER);
        boolean recentSuccess = backups.stream()
                .filter(b -> b.status() == BackupStatus.SUCCESS)
                .anyMatch(b -> timestamp(b).isAfter(cutoff));
        if (recentSuccess) {
            alerts.resolve(new AlertKey(AlertType.BACKUP_STALE, projectId, null), now);
            return;
        }
        openAlert(AlertType.BACKUP_STALE, projectId, "No successful backup within " + STALE_AFTER.toHours() + "h", now);
    }

    private void openAlert(AlertType type, String projectId, String message, Instant now) {
        AlertKey key = new AlertKey(type, projectId, null);
        if (alerts.findOpen(key).isPresent()) {
            return;
        }
        UUID ruleId = rules.findEnabled().stream()
                .filter(rule -> rule.type() == type && rule.projectId() == null)
                .map(AlertRule::id)
                .findFirst()
                .orElse(null);
        alerts.open(new Alert(
                UUID.randomUUID(),
                ruleId,
                projectId,
                null,
                AlertStatus.ACTIVE,
                message,
                now,
                null,
                null,
                type));
    }

    private static Instant timestamp(Backup backup) {
        return backup.finishedAt() != null ? backup.finishedAt() : backup.createdAt();
    }
}
