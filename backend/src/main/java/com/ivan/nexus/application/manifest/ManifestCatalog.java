package com.ivan.nexus.application.manifest;

import java.util.Set;

public interface ManifestCatalog {
    Set<String> discoverProjectIds();

    boolean exists(String projectId);

    LoadedManifest loadRequired(String projectId);
}
