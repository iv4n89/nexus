package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.github.ProjectGitHubLink;
import com.ivan.nexus.domain.manifest.ProjectManifest;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ManagedProjectStore {
    void upsert(ProjectManifest manifest, Path workingDirectory, Path manifestPath);

    /**
     * Inserts a {@code projects} row if missing so FK-backed records (domains) can persist
     * for Docker-discovered stacks that never went through deploy.
     */
    void ensureRegistered(String projectId, String workingDirectory, String manifestPath);

    void linkGitHub(String projectId, String owner, String repo, String branch);

    Optional<ProjectGitHubLink> findGitHubLink(String projectId);

    List<ProjectGitHubLink> findByGitHubRepository(String owner, String repo, String branch);

    void updateLastRemoteSha(String projectId, String sha);

    void setAutodeployEnabled(String projectId, boolean enabled);
}
