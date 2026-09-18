package com.ivan.nexus.infrastructure.persistence.project;

import com.ivan.nexus.application.github.ProjectGitHubLink;
import com.ivan.nexus.application.github.ProjectGitHubLinkStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaProjectGitHubLinkStore implements ProjectGitHubLinkStore {
    private final ManagedProjectJpaRepository projects;

    public JpaProjectGitHubLinkStore(ManagedProjectJpaRepository projects) {
        this.projects = projects;
    }

    @Override
    public List<ProjectGitHubLink> findByRepository(String owner, String repo, String branch) {
        return projects.findByGithubOwnerAndGithubRepoAndGithubBranch(owner, repo, branch).stream()
                .map(JpaProjectGitHubLinkStore::toLink)
                .toList();
    }

    @Override
    public void updateLastRemoteSha(String projectId, String sha) {
        projects.updateLastRemoteSha(projectId, sha);
    }

    private static ProjectGitHubLink toLink(ManagedProjectEntity entity) {
        return new ProjectGitHubLink(
                entity.getId(),
                entity.getGithubOwner(),
                entity.getGithubRepo(),
                entity.getGithubBranch(),
                entity.isAutoDeploy(),
                entity.getGithubLastRemoteSha());
    }
}
