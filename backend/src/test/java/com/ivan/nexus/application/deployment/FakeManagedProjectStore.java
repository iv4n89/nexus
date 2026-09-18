package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.manifest.ProjectManifest;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class FakeManagedProjectStore implements ManagedProjectStore {
    final Map<String, SavedProject> projects = new LinkedHashMap<>();

    @Override
    public void upsert(ProjectManifest manifest, Path workingDirectory, Path manifestPath) {
        String id = manifest.project().id();
        String name = manifest.project().name();
        projects.put(id, new SavedProject(
                id,
                name == null || name.isBlank() ? id : name,
                manifest.project().description(),
                workingDirectory,
                manifestPath));
    }

    record SavedProject(
            String id,
            String name,
            String description,
            Path workingDirectory,
            Path manifestPath) {
    }
}
