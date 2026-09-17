package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class DeployProject {
    private final ManifestCatalog manifests;
    private final DeploymentCommandRunner runner;

    public DeployProject(
            ManifestCatalog manifests,
            ManagedProjectStore projects,
            DeploymentStore deployments,
            DeploymentStreamHub hub,
            ProcessExecutor processExecutor,
            HealthChecker healthChecker,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users,
            @Qualifier("deploymentExecutor") Executor sseExecutor) {
        this.manifests = manifests;
        this.runner = new DeploymentCommandRunner(
                projects,
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
        LoadedManifest loaded = manifests.loadRequired(projectId);
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
