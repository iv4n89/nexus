package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectEntity;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectJpaRepository;

import java.nio.file.Path;

final class ManagedProjectUpsert {
    private final ManagedProjectJpaRepository projects;

    ManagedProjectUpsert(ManagedProjectJpaRepository projects) {
        this.projects = projects;
    }

    void upsertProject(ProjectManifest manifest, Path workingDirectory, Path manifestPath) {
        String id = manifest.project().id();
        String name = blankToId(manifest.project().name(), id);
        String description = manifest.project().description();
        String directory = workingDirectory.toString();
        String path = manifestPath.toAbsolutePath().normalize().toString();
        ManagedProjectEntity existing = projects.findById(id).orElse(null);
        if (existing == null) {
            projects.save(new ManagedProjectEntity(id, name, description, directory, path));
            return;
        }
        existing.applyManifest(name, description, directory, path);
        projects.save(existing);
    }

    static String blankToId(String name, String id) {
        return name == null || name.isBlank() ? id : name;
    }
}
