package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.manifest.ManifestValidator;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.manifest.YamlManifestLoader;

import java.nio.file.Files;
import java.nio.file.Path;

final class LoadedManifest {
    private final ProjectManifest manifest;
    private final Path manifestPath;

    private LoadedManifest(ProjectManifest manifest, Path manifestPath) {
        this.manifest = manifest;
        this.manifestPath = manifestPath;
    }

    static LoadedManifest load(
            String projectId,
            Path allowedRoot,
            YamlManifestLoader loader,
            ManifestValidator validator) {
        Path manifestPath = allowedRoot.resolve(projectId).resolve("nexus.yml");
        if (!Files.isRegularFile(manifestPath)) {
            throw new DomainException(NexusErrorCode.MANIFEST_NOT_FOUND, "Manifest not found");
        }
        ProjectManifest manifest = loader.load(manifestPath);
        if (!projectId.equals(manifest.project().id())) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "project.id does not match");
        }
        validator.validate(manifest);
        return new LoadedManifest(manifest, manifestPath);
    }

    ProjectManifest manifest() {
        return manifest;
    }

    Path manifestPath() {
        return manifestPath;
    }
}
