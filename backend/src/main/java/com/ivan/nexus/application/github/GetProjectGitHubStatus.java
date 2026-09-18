package com.ivan.nexus.application.github;

import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetProjectGitHubStatus {
    private final ManagedProjectStore projects;
    private final DeploymentStore deployments;
    private final GitHubClient gitHubClient;
    private final RequireGitHubAccessToken accessToken;

    public GetProjectGitHubStatus(
            ManagedProjectStore projects,
            DeploymentStore deployments,
            GitHubClient gitHubClient,
            RequireGitHubAccessToken accessToken) {
        this.projects = projects;
        this.deployments = deployments;
        this.gitHubClient = gitHubClient;
        this.accessToken = accessToken;
    }

    @Transactional(readOnly = true)
    public ProjectGitHubStatusView execute(String projectId) {
        ProjectGitHubLink link = projects.findGitHubLink(projectId)
                .orElseThrow(() -> new DomainException(
                        NexusErrorCode.PROJECT_GITHUB_NOT_LINKED,
                        "Project is not linked to a GitHub repository"));
        String remoteHeadSha = resolveRemoteHeadSha(link);
        String deployedCommitSha = deployments.findProjectHistoryNewestFirst(projectId).stream()
                .filter(deployment -> deployment.status() == DeploymentStatus.SUCCESS)
                .map(Deployment::commitSha)
                .filter(sha -> sha != null && !sha.isBlank())
                .findFirst()
                .orElse(null);
        boolean upToDate = remoteHeadSha != null && remoteHeadSha.equals(deployedCommitSha);
        return new ProjectGitHubStatusView(
                projectId,
                link.owner(),
                link.repo(),
                link.branch(),
                remoteHeadSha,
                deployedCommitSha,
                upToDate,
                link.autodeployEnabled());
    }

    private String resolveRemoteHeadSha(ProjectGitHubLink link) {
        if (link.lastRemoteSha() != null && !link.lastRemoteSha().isBlank()) {
            return link.lastRemoteSha();
        }
        return gitHubClient.getBranchHead(
                accessToken.execute(),
                link.owner(),
                link.repo(),
                link.branch());
    }
}
