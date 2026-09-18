package com.ivan.nexus.application.security;

import com.ivan.nexus.application.manifest.ManifestCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Optional nightly (configurable) scan for every project that has a usable manifest.
 */
@Service
@ConditionalOnProperty(name = "nexus.security.trivy.scheduler-enabled", havingValue = "true")
public class ScheduledSecurityScan {
    private static final Logger log = LoggerFactory.getLogger(ScheduledSecurityScan.class);

    private final ManifestCatalog manifests;
    private final RunSecurityScan runSecurityScan;

    public ScheduledSecurityScan(ManifestCatalog manifests, RunSecurityScan runSecurityScan) {
        this.manifests = manifests;
        this.runSecurityScan = runSecurityScan;
    }

    @Scheduled(cron = "${nexus.security.trivy.cron:0 0 4 * * *}")
    public void execute() {
        for (String projectId : manifests.discoverProjectIds()) {
            try {
                runSecurityScan.execute(projectId);
            } catch (RuntimeException ex) {
                log.warn("Scheduled security scan failed for {}: {}", projectId, ex.getMessage());
            }
        }
    }
}
