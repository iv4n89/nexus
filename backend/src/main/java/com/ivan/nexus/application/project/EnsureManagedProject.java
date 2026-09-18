package com.ivan.nexus.application.project;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Ensures a {@code projects} row exists for Docker-discovered project ids.
 * Domain inserts FK to that table; listing a compose stack must not 500.
 */
@Service
public class EnsureManagedProject {
    private final ManagedProjectStore projects;
    private final ManifestCatalog manifests;
    private final ProjectDotEnvStore dotEnvStore;

    public EnsureManagedProject(
            ManagedProjectStore projects,
            ManifestCatalog manifests,
            ProjectDotEnvStore dotEnvStore) {
        this.projects = projects;
        this.manifests = manifests;
        this.dotEnvStore = dotEnvStore;
    }

    public void execute(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        Path working = Path.of(projectId);
        Path manifestPath = working.resolve("nexus.yml");
        Path located = dotEnvStore.locateDirectory(projectId).orElse(null);
        if (located != null) {
            working = located;
            manifestPath = working.resolve("nexus.yml");
        }
        try {
            LoadedManifest loaded = manifests.loadRequired(projectId);
            String declared = loaded.manifest().project().workingDirectory();
            if (declared != null && !declared.isBlank()) {
                working = Path.of(declared);
            }
            if (loaded.manifestPath() != null) {
                manifestPath = loaded.manifestPath();
            }
        } catch (RuntimeException ignored) {
            // compose-only projects have no nexus.yml
        }
        projects.ensureRegistered(projectId, working.toString(), manifestPath.toString());
    }
}
