package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.manifest.ProjectManifest;

import java.nio.file.Path;

public interface ManagedProjectStore {
    void upsert(ProjectManifest manifest, Path workingDirectory, Path manifestPath);
}
