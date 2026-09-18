package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.PostRestoreHealthCheck;
import com.ivan.nexus.application.deployment.HealthChecker;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.manifest.ProjectManifest;

import java.time.Duration;

/**
 * Runs the project's manifest health URL after restore when configured.
 */
public class ManifestPostRestoreHealthCheck implements PostRestoreHealthCheck {
    private final ManifestCatalog manifests;
    private final HealthChecker healthChecker;

    public ManifestPostRestoreHealthCheck(ManifestCatalog manifests, HealthChecker healthChecker) {
        this.manifests = manifests;
        this.healthChecker = healthChecker;
    }

    @Override
    public boolean verify(String projectId) {
        LoadedManifest loaded;
        try {
            loaded = manifests.loadRequired(projectId);
        } catch (RuntimeException ex) {
            return true;
        }
        ProjectManifest.HealthBlock health = loaded.manifest().health();
        if (health == null || health.url() == null || health.url().isBlank()) {
            return true;
        }
        int seconds = health.timeoutSeconds() == null ? 10 : health.timeoutSeconds();
        return healthChecker.check(health.url(), Duration.ofSeconds(Math.max(1, seconds)));
    }
}
