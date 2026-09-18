package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.github.ProjectGitHubLink;
import com.ivan.nexus.domain.manifest.ProjectManifest;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeManagedProjectStore implements ManagedProjectStore {
    final Map<String, SavedProject> projects = new LinkedHashMap<>();
    final Map<String, ProjectGitHubLink> githubLinks = new LinkedHashMap<>();

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

    @Override
    public void ensureRegistered(String projectId, String workingDirectory, String manifestPath) {
        projects.putIfAbsent(projectId, new SavedProject(
                projectId,
                projectId,
                null,
                Path.of(workingDirectory),
                Path.of(manifestPath)));
    }

    @Override
    public void linkGitHub(String projectId, String owner, String repo, String branch) {
        if (!projects.containsKey(projectId)) {
            throw new IllegalStateException("Project not found: " + projectId);
        }
        githubLinks.put(projectId, ProjectGitHubLink.of(projectId, owner, repo, branch));
    }

    @Override
    public Optional<ProjectGitHubLink> findGitHubLink(String projectId) {
        return Optional.ofNullable(githubLinks.get(projectId));
    }

    @Override
    public List<ProjectGitHubLink> findByGitHubRepository(String owner, String repo, String branch) {
        return githubLinks.values().stream()
                .filter(link -> link.owner().equals(owner)
                        && link.repo().equals(repo)
                        && link.branch().equals(branch))
                .toList();
    }

    @Override
    public void updateLastRemoteSha(String projectId, String sha) {
        ProjectGitHubLink link = githubLinks.get(projectId);
        if (link == null) {
            throw new IllegalStateException("Project not found: " + projectId);
        }
        githubLinks.put(projectId, new ProjectGitHubLink(
                link.projectId(),
                link.owner(),
                link.repo(),
                link.branch(),
                link.autodeployEnabled(),
                sha));
    }

    @Override
    public void setAutodeployEnabled(String projectId, boolean enabled) {
        ProjectGitHubLink link = githubLinks.get(projectId);
        if (link == null) {
            throw new IllegalStateException("Project not found: " + projectId);
        }
        githubLinks.put(projectId, new ProjectGitHubLink(
                link.projectId(),
                link.owner(),
                link.repo(),
                link.branch(),
                enabled,
                link.lastRemoteSha()));
    }

    record SavedProject(
            String id,
            String name,
            String description,
            Path workingDirectory,
            Path manifestPath) {
    }
}
