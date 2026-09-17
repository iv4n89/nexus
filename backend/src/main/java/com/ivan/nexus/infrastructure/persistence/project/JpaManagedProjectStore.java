package com.ivan.nexus.infrastructure.persistence.project;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class JpaManagedProjectStore implements ManagedProjectStore {
    private final ManagedProjectJpaRepository repository;

    public JpaManagedProjectStore(ManagedProjectJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void upsert(ProjectManifest manifest, Path workingDirectory, Path manifestPath) {
        String id = manifest.project().id();
        String name = blankToId(manifest.project().name(), id);
        String description = manifest.project().description();
        String directory = workingDirectory.toString();
        String path = manifestPath.toString();
        repository.upsertProject(id, name, description, directory, path);
    }

    static String blankToId(String name, String id) {
        return name == null || name.isBlank() ? id : name;
    }
}
