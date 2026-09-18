package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.env.ProjectEnvStore;
import com.ivan.nexus.application.env.StoredProjectEnvVar;
import com.ivan.nexus.application.secrets.SecretStore;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

final class DeploymentCommandRunner {
    static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final ManagedProjectStore projects;
    private final DeploymentStore deployments;
    private final DeploymentProgress progress;
    private final ProcessExecutor processExecutor;
    private final HealthChecker healthChecker;
    private final ProjectEnvStore projectEnvStore;
    private final SecretStore secretStore;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;
    private final Executor sseExecutor;

    DeploymentCommandRunner(
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
            Executor sseExecutor) {
        this.projects = projects;
        this.deployments = deployments;
        this.progress = progress;
        this.processExecutor = processExecutor;
        this.healthChecker = healthChecker;
        this.projectEnvStore = projectEnvStore;
        this.secretStore = secretStore;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
        this.users = users;
        this.sseExecutor = sseExecutor;
    }

    Deployment start(
            String projectId,
            String username,
            ProjectManifest manifest,
            Path manifestPath,
            String command,
            String kind,
            AuditAction auditAction,
            String commitSha) {
        if (deployments.hasActiveDeployment(projectId)) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_IN_PROGRESS, "Deployment already in progress");
        }

        Path workingDirectory = Path.of(manifest.project().workingDirectory()).toAbsolutePath().normalize();
        Path normalizedManifestPath = manifestPath.toAbsolutePath().normalize();
        projects.upsert(manifest, workingDirectory, normalizedManifestPath);

        UUID id = UUID.randomUUID();
        deployments.createPending(id, projectId, username, kind, commitSha);
        Deployment snapshot = deployments.markRunning(id, Instant.now());

        sseExecutor.execute(() -> runAsync(id, manifest, workingDirectory, command, kind, auditAction, username));
        return snapshot;
    }

    private void runAsync(
            UUID id,
            ProjectManifest manifest,
            Path workingDirectory,
            String command,
            String kind,
            AuditAction auditAction,
            String username) {
        Deployment deployment = deployments.findById(id).orElseThrow();
        List<String> summaryLines = new ArrayList<>();
        Copy copy = Copy.forKind(kind);
        Map<String, String> environment = loadEnvironment(deployment.projectId());
        List<String> redactValues = List.copyOf(environment.values());
        try {
            emit(id, summaryLines, copy.started, redactValues);
            record(ActivityType.DEPLOYMENT_STARTED, deployment, copy.started, kind);

            List<String> tokens = List.of(command.trim().split("\\s+"));
            Path executable = workingDirectory.resolve(tokens.getFirst()).normalize();
            if (!executable.startsWith(workingDirectory)
                    || !Files.isRegularFile(executable)
                    || !Files.isExecutable(executable)) {
                emit(id, summaryLines,
                        "Command is not an executable file under the project directory: " + executable,
                        redactValues);
                finish(deployment, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy,
                        redactValues);
                return;
            }

            List<String> argv = new ArrayList<>();
            argv.add(executable.toString());
            argv.addAll(tokens.subList(1, tokens.size()));

            int exit = processExecutor.run(
                    workingDirectory,
                    argv,
                    environment,
                    line -> emit(id, summaryLines, line, redactValues),
                    SCRIPT_TIMEOUT);
            deployments.recordExitCode(id, exit);
            if (exit != 0) {
                finish(deployment, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy,
                        redactValues);
                return;
            }

            String healthUrl = manifest.health() == null ? null : manifest.health().url();
            if (healthUrl != null && !healthUrl.isBlank()) {
                emit(id, summaryLines, "health check", redactValues);
                Integer seconds = manifest.health().timeoutSeconds();
                Duration timeout = seconds == null || seconds <= 0
                        ? DEFAULT_HEALTH_TIMEOUT
                        : Duration.ofSeconds(seconds);
                boolean ok = healthChecker.check(healthUrl, timeout);
                deployments.recordHealthResult(id, ok);
                if (ok) {
                    emit(id, summaryLines, "health check OK", redactValues);
                    finish(deployment, summaryLines, DeploymentStatus.SUCCESS, username, kind, auditAction, copy,
                            redactValues);
                } else {
                    emit(id, summaryLines, "health check FAILED", redactValues);
                    record(ActivityType.HEALTH_CHECK_FAILED, deployment, "health check failed", kind);
                    finish(deployment, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy,
                            redactValues);
                }
                return;
            }

            finish(deployment, summaryLines, DeploymentStatus.SUCCESS, username, kind, auditAction, copy,
                    redactValues);
        } catch (RuntimeException ex) {
            emit(id, summaryLines, copy.kindLabel + " error: " + redact(ex.getMessage(), redactValues),
                    redactValues);
            finish(deployment, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy,
                    redactValues);
        }
    }

    private Map<String, String> loadEnvironment(String projectId) {
        Map<String, String> environment = new LinkedHashMap<>();
        for (StoredProjectEnvVar envVar : projectEnvStore.listByProject(projectId)) {
            environment.put(envVar.name(), secretStore.decrypt(envVar.encryptedValue()));
        }
        return environment;
    }

    private void finish(
            Deployment deployment,
            List<String> summaryLines,
            DeploymentStatus status,
            String username,
            String kind,
            AuditAction auditAction,
            Copy copy,
            List<String> redactValues) {
        if (status == DeploymentStatus.SUCCESS) {
            emit(deployment.id(), summaryLines, "DEPLOYMENT SUCCESS", redactValues);
            record(ActivityType.DEPLOYMENT_SUCCESS, deployment, copy.successful, kind);
        } else {
            emit(deployment.id(), summaryLines, "DEPLOYMENT FAILED", redactValues);
            record(ActivityType.DEPLOYMENT_FAILED, deployment, copy.failed, kind);
        }
        deployments.finish(
                deployment.id(),
                status,
                Instant.now(),
                DeploymentSummary.summarize(summaryLines));
        progress.complete(deployment.id());
        UUID userId = users.findIdByUsername(username).orElseThrow();
        recordAudit.execute(
                userId,
                auditAction,
                deployment.projectId(),
                null,
                null,
                Map.of("deploymentId", deployment.id().toString(), "status", status.name()));
    }

    private void emit(UUID id, List<String> summaryLines, String line, List<String> redactValues) {
        String safe = redact(line, redactValues);
        summaryLines.add(safe);
        progress.append(id, safe);
    }

    private static String redact(String line, List<String> redactValues) {
        if (line == null) {
            return "";
        }
        String out = line;
        for (String value : redactValues) {
            if (value != null && !value.isBlank()) {
                out = out.replace(value, "***");
            }
        }
        return out;
    }

    private void record(ActivityType type, Deployment deployment, String message, String kind) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("deploymentId", deployment.id().toString());
        metadata.put("kind", kind);
        recordActivity.execute(type, deployment.projectId(), null, message, metadata);
    }

    private record Copy(String kindLabel, String started, String successful, String failed) {
        static Copy forKind(String kind) {
            if ("rollback".equals(kind)) {
                return new Copy("rollback", "rollback started", "rollback successful", "rollback failed");
            }
            return new Copy("deployment", "deployment started", "deployment successful", "deployment failed");
        }
    }
}
