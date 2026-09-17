package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.infrastructure.manifest.YamlManifestLoader;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectJpaRepository;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.Executor;

@Service
public class DeployProject {
    private final YamlManifestLoader loader;
    private final Path allowedRoot;
    private final DeploymentCommandRunner runner;

    public DeployProject(
            YamlManifestLoader loader,
            @Qualifier("manifestAllowedRoot") Path allowedRoot,
            ManagedProjectJpaRepository projects,
            DeploymentJpaRepository deployments,
            DeploymentStreamHub hub,
            ProcessExecutor processExecutor,
            HealthChecker healthChecker,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users,
            @Qualifier("deploymentExecutor") Executor sseExecutor) {
        this.loader = loader;
        this.allowedRoot = allowedRoot;
        this.runner = new DeploymentCommandRunner(
                new ManagedProjectUpsert(projects),
                deployments,
                hub,
                processExecutor,
                healthChecker,
                recordAudit,
                recordActivity,
                users,
                sseExecutor);
    }

    public Deployment execute(String projectId, String username) {
        LoadedManifest loaded = LoadedManifest.load(projectId, allowedRoot, loader);
        return runner.start(
                projectId,
                username,
                loaded.manifest(),
                loaded.manifestPath(),
                loaded.manifest().deployment().command(),
                "deploy",
                AuditAction.DEPLOY);
    }
}
