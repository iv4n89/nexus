package com.ivan.nexus.application.backup;

import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.backup.BackupKind;
import com.ivan.nexus.domain.backup.BackupPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Daily backup stub: dumps Postgres for projects with manifests when policy is enabled
 * (or when no policy row exists yet, skip — only run when policy.enabled).
 */
@Service
@ConditionalOnProperty(name = "nexus.backup.scheduler-enabled", havingValue = "true")
public class ScheduledBackup {
    private static final Logger log = LoggerFactory.getLogger(ScheduledBackup.class);

    private final ManifestCatalog manifests;
    private final BackupPolicyStore policies;
    private final RunBackup runBackup;

    public ScheduledBackup(ManifestCatalog manifests, BackupPolicyStore policies, RunBackup runBackup) {
        this.manifests = manifests;
        this.policies = policies;
        this.runBackup = runBackup;
    }

    @Scheduled(cron = "${nexus.backup.cron:0 0 3 * * *}")
    public void execute() {
        for (String projectId : manifests.discoverProjectIds()) {
            BackupPolicy policy = policies.findByProjectId(projectId).orElse(null);
            if (policy == null || !policy.enabled()) {
                continue;
            }
            try {
                runBackup.execute(projectId, BackupKind.SCHEDULED);
            } catch (RuntimeException ex) {
                log.warn("Scheduled backup failed for {}: {}", projectId, ex.getMessage());
            }
        }
    }
}
