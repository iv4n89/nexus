package com.ivan.nexus.application.manifest;

import com.ivan.nexus.domain.manifest.ProjectManifest;

import java.nio.file.Path;

public record LoadedManifest(ProjectManifest manifest, Path manifestPath) {
}
