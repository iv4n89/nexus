package com.ivan.nexus.infrastructure.persistence.project;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.application.github.ProjectGitHubLink;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

@Component
public class JpaManagedProjectStore implements ManagedProjectStore {
    private final ManagedProjectJpaRepository repository;

    JpaManagedProjectStore(ManagedProjectJpaRepository repository) {
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

    @Override
    public void linkGitHub(String projectId, String owner, String repo, String branch) {
        int updated = repository.linkGitHub(projectId, owner, repo, branch);
        if (updated == 0) {
            throw new DomainException(NexusErrorCode.PROJECT_NOT_FOUND, "Project not found");
        }
    }

    @Override
    public Optional<ProjectGitHubLink> findGitHubLink(String projectId) {
        return repository.findById(projectId)
                .filter(entity -> entity.getGithubOwner() != null
                        && entity.getGithubRepo() != null
                        && entity.getGithubBranch() != null)
                .map(entity -> new ProjectGitHubLink(
                        entity.getId(),
                        entity.getGithubOwner(),
                        entity.getGithubRepo(),
                        entity.getGithubBranch()));
    }

    static String blankToId(String name, String id) {
        return name == null || name.isBlank() ? id : name;
    }
}
