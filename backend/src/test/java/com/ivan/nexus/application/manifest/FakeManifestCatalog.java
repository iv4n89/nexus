package com.ivan.nexus.application.manifest;

import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class FakeManifestCatalog implements ManifestCatalog {
    private final Map<String, LoadedManifest> manifests = new LinkedHashMap<>();
    private final Map<String, RuntimeException> failures = new LinkedHashMap<>();

    public FakeManifestCatalog add(String projectId, ProjectManifest manifest, Path manifestPath) {
        manifests.put(projectId, new LoadedManifest(manifest, manifestPath));
        return this;
    }

    public FakeManifestCatalog fail(String projectId, RuntimeException failure) {
        failures.put(projectId, failure);
        return this;
    }

    @Override
    public Set<String> discoverProjectIds() {
        var projectIds = new java.util.LinkedHashSet<>(manifests.keySet());
        projectIds.addAll(failures.keySet());
        return Set.copyOf(projectIds);
    }

    @Override
    public boolean exists(String projectId) {
        return manifests.containsKey(projectId) || failures.containsKey(projectId);
    }

    @Override
    public LoadedManifest loadRequired(String projectId) {
        RuntimeException failure = failures.get(projectId);
        if (failure != null) {
            throw failure;
        }
        LoadedManifest loaded = manifests.get(projectId);
        if (loaded == null) {
            throw new DomainException(NexusErrorCode.MANIFEST_NOT_FOUND, "Manifest not found");
        }
        return loaded;
    }
}
