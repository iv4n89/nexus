package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.deployment.DeploymentTransitions;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

final class DeploymentCommandRunner {
    static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final List<DeploymentStatus> IN_PROGRESS =
            List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING);

    private final ManagedProjectUpsert projectUpsert;
    private final DeploymentJpaRepository deployments;
    private final DeploymentStreamHub hub;
    private final ProcessExecutor processExecutor;
    private final HealthChecker healthChecker;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final UserDirectory users;
    private final Executor sseExecutor;

    DeploymentCommandRunner(
            ManagedProjectUpsert projectUpsert,
            DeploymentJpaRepository deployments,
            DeploymentStreamHub hub,
            ProcessExecutor processExecutor,
            HealthChecker healthChecker,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            UserDirectory users,
            Executor sseExecutor) {
        this.projectUpsert = projectUpsert;
        this.deployments = deployments;
        this.hub = hub;
        this.processExecutor = processExecutor;
        this.healthChecker = healthChecker;
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
            AuditAction auditAction) {
        if (deployments.existsByProjectIdAndStatusIn(projectId, IN_PROGRESS)) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_IN_PROGRESS, "Deployment already in progress");
        }

        Path workingDirectory = Path.of(manifest.project().workingDirectory()).toAbsolutePath().normalize();
        projectUpsert.upsertProject(manifest, workingDirectory, manifestPath);

        UUID id = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("kind", kind);
        DeploymentEntity entity = new DeploymentEntity(
                id,
                projectId,
                DeploymentStatus.PENDING,
                null,
                null,
                username,
                null,
                null,
                null,
                null,
                metadata);
        try {
            deployments.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_IN_PROGRESS, "Deployment already in progress");
        }
        entity.applyStatus(DeploymentTransitions.next(entity.getStatus(), DeploymentStatus.RUNNING));
        entity.setStartedAt(Instant.now());
        deployments.save(entity);

        Deployment snapshot = toDomain(entity);
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
        DeploymentEntity entity = deployments.findById(id).orElseThrow();
        List<String> summaryLines = new ArrayList<>();
        Copy copy = Copy.forKind(kind);
        try {
            emit(id, summaryLines, copy.started);
            record(ActivityType.DEPLOYMENT_STARTED, entity, copy.started, kind);

            List<String> tokens = List.of(command.trim().split("\\s+"));
            Path executable = workingDirectory.resolve(tokens.getFirst()).normalize();
            if (!executable.startsWith(workingDirectory)
                    || !Files.isRegularFile(executable)
                    || !Files.isExecutable(executable)) {
                emit(id, summaryLines, "Command is not an executable file under the project directory: " + executable);
                finish(entity, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy);
                return;
            }

            List<String> argv = new ArrayList<>();
            argv.add(executable.toString());
            argv.addAll(tokens.subList(1, tokens.size()));

            int exit = processExecutor.run(
                    workingDirectory,
                    argv,
                    line -> emit(id, summaryLines, line),
                    SCRIPT_TIMEOUT);
            entity.setExitCode(exit);
            if (exit != 0) {
                finish(entity, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy);
                return;
            }

            String healthUrl = manifest.health() == null ? null : manifest.health().url();
            if (healthUrl != null && !healthUrl.isBlank()) {
                emit(id, summaryLines, "health check");
                Integer seconds = manifest.health().timeoutSeconds();
                Duration timeout = seconds == null || seconds <= 0
                        ? DEFAULT_HEALTH_TIMEOUT
                        : Duration.ofSeconds(seconds);
                boolean ok = healthChecker.check(healthUrl, timeout);
                entity.setHealthOk(ok);
                if (ok) {
                    emit(id, summaryLines, "health check OK");
                    finish(entity, summaryLines, DeploymentStatus.SUCCESS, username, kind, auditAction, copy);
                } else {
                    emit(id, summaryLines, "health check FAILED");
                    record(ActivityType.HEALTH_CHECK_FAILED, entity, "health check failed", kind);
                    finish(entity, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy);
                }
                return;
            }

            finish(entity, summaryLines, DeploymentStatus.SUCCESS, username, kind, auditAction, copy);
        } catch (RuntimeException ex) {
            emit(id, summaryLines, copy.kindLabel + " error: " + ex.getMessage());
            finish(entity, summaryLines, DeploymentStatus.FAILED, username, kind, auditAction, copy);
        }
    }

    private void finish(
            DeploymentEntity entity,
            List<String> summaryLines,
            DeploymentStatus status,
            String username,
            String kind,
            AuditAction auditAction,
            Copy copy) {
        if (status == DeploymentStatus.SUCCESS) {
            emit(entity.getId(), summaryLines, "DEPLOYMENT SUCCESS");
            record(ActivityType.DEPLOYMENT_SUCCESS, entity, copy.successful, kind);
        } else {
            emit(entity.getId(), summaryLines, "DEPLOYMENT FAILED");
            record(ActivityType.DEPLOYMENT_FAILED, entity, copy.failed, kind);
        }
        entity.applyStatus(DeploymentTransitions.next(entity.getStatus(), status));
        entity.setFinishedAt(Instant.now());
        entity.setOutputSummary(DeploymentSummary.summarize(summaryLines));
        deployments.save(entity);
        hub.complete(entity.getId());
        UUID userId = users.findIdByUsername(username).orElseThrow();
        recordAudit.execute(
                userId,
                auditAction,
                entity.getProjectId(),
                null,
                null,
                Map.of("deploymentId", entity.getId().toString(), "status", status.name()));
    }

    private void emit(UUID id, List<String> summaryLines, String line) {
        summaryLines.add(line);
        hub.append(id, line);
    }

    private void record(ActivityType type, DeploymentEntity entity, String message, String kind) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("deploymentId", entity.getId().toString());
        metadata.put("kind", kind);
        recordActivity.execute(type, entity.getProjectId(), null, message, metadata);
    }

    static Deployment toDomain(DeploymentEntity entity) {
        return new Deployment(
                entity.getId(),
                entity.getProjectId(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getTriggeredBy(),
                entity.getCommitSha(),
                entity.getExitCode(),
                entity.getOutputSummary(),
                entity.getHealthOk());
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
