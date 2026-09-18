package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.env.ProjectEnvStore;
import com.ivan.nexus.application.github.GitHubClient;
import com.ivan.nexus.application.github.ProjectGitHubLink;
import com.ivan.nexus.application.github.RequireGitHubAccessToken;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class DeployProject {
    private final ManifestCatalog manifests;
    private final ManagedProjectStore projects;
    private final GitHubClient gitHubClient;
    private final RequireGitHubAccessToken accessToken;
    private final DeploymentCommandRunner runner;

    public DeployProject(
            ManifestCatalog manifests,
            ManagedProjectStore projects,
            DeploymentStore deployments,
            DeploymentProgress progress,
            ProcessExecutor processExecutor,
            HealthChecker healthChecker,
            ProjectEnvStore projectEnvStore,
            SecretStore secretStore,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users,
            GitHubClient gitHubClient,
            RequireGitHubAccessToken accessToken,
            @Qualifier("deploymentExecutor") Executor sseExecutor) {
        this.manifests = manifests;
        this.projects = projects;
        this.gitHubClient = gitHubClient;
        this.accessToken = accessToken;
        this.runner = new DeploymentCommandRunner(
                projects,
                deployments,
                progress,
                processExecutor,
                healthChecker,
                projectEnvStore,
                secretStore,
                recordAudit,
                recordActivity,
                users,
                sseExecutor);
    }

    public Deployment execute(String projectId, String username) {
        LoadedManifest loaded = manifests.loadRequired(projectId);
        String commitSha = resolveCommitSha(projectId);
        return runner.start(
                projectId,
                username,
                loaded.manifest(),
                loaded.manifestPath(),
                loaded.manifest().deployment().command(),
                "deploy",
                AuditAction.DEPLOY,
                commitSha);
    }

    private String resolveCommitSha(String projectId) {
        return projects.findGitHubLink(projectId)
                .map(this::fetchBranchHead)
                .orElse(null);
    }

    private String fetchBranchHead(ProjectGitHubLink link) {
        return gitHubClient.getBranchHead(
                accessToken.execute(),
                link.owner(),
                link.repo(),
                link.branch());
    }
}
