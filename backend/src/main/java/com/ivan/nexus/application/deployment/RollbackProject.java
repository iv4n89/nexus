package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.env.ProjectEnvStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class RollbackProject {
    private final ManifestCatalog manifests;
    private final DeploymentCommandRunner runner;

    public RollbackProject(
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
            @Qualifier("deploymentExecutor") Executor sseExecutor) {
        this.manifests = manifests;
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
        String command = rollbackCommand(loaded.manifest());
        if (command == null) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Rollback command is not configured");
        }
        return runner.start(
                projectId,
                username,
                loaded.manifest(),
                loaded.manifestPath(),
                command,
                "rollback",
                AuditAction.ROLLBACK);
    }

    private static String rollbackCommand(ProjectManifest manifest) {
        if (manifest.rollback() == null || manifest.rollback().command() == null) {
            return null;
        }
        String command = manifest.rollback().command().trim();
        return command.isEmpty() ? null : command;
    }
}
