package com.ivan.nexus.application.deployment;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.deployment.DeploymentTransitions;
import com.ivan.nexus.domain.manifest.ManifestValidator;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import com.ivan.nexus.infrastructure.manifest.YamlManifestLoader;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectEntity;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectJpaRepository;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import com.ivan.nexus.infrastructure.sse.DeploymentStreamHub;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class DeployProject {
    static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final int SUMMARY_LIMIT = 8000;
    private static final List<DeploymentStatus> IN_PROGRESS =
            List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING);

    private final YamlManifestLoader loader;
    private final ManifestValidator validator;
    private final Path allowedRoot;
    private final ManagedProjectJpaRepository projects;
    private final DeploymentJpaRepository deployments;
    private final DeploymentStreamHub hub;
    private final ProcessExecutor processExecutor;
    private final HealthChecker healthChecker;
    private final RecordAudit recordAudit;
    private final UserJpaRepository users;
    private final Executor sseExecutor;

    public DeployProject(
            YamlManifestLoader loader,
            ManifestValidator validator,
            NexusProperties properties,
            ManagedProjectJpaRepository projects,
            DeploymentJpaRepository deployments,
            DeploymentStreamHub hub,
            ProcessExecutor processExecutor,
            HealthChecker healthChecker,
            RecordAudit recordAudit,
            UserJpaRepository users,
            @Qualifier("sseExecutor") Executor sseExecutor) {
        this.loader = loader;
        this.validator = validator;
        this.allowedRoot = Path.of(properties.getManifest().getAllowedRoot()).toAbsolutePath().normalize();
        this.projects = projects;
        this.deployments = deployments;
        this.hub = hub;
        this.processExecutor = processExecutor;
        this.healthChecker = healthChecker;
        this.recordAudit = recordAudit;
        this.users = users;
        this.sseExecutor = sseExecutor;
    }

    public Deployment execute(String projectId, String username) {
        Path manifestPath = allowedRoot.resolve(projectId).resolve("nexus.yml");
        if (!Files.isRegularFile(manifestPath)) {
            throw new DomainException(NexusErrorCode.MANIFEST_NOT_FOUND, "Manifest not found");
        }

        ProjectManifest manifest = loader.load(manifestPath);
        if (!projectId.equals(manifest.project().id())) {
            throw new DomainException(NexusErrorCode.MANIFEST_INVALID, "project.id does not match");
        }
        validator.validate(manifest);

        if (deployments.existsByProjectIdAndStatusIn(projectId, IN_PROGRESS)) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_IN_PROGRESS, "Deployment already in progress");
        }

        Path workingDirectory = Path.of(manifest.project().workingDirectory()).toAbsolutePath().normalize();
        upsertProject(manifest, workingDirectory, manifestPath);

        UUID id = UUID.randomUUID();
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
                null);
        try {
            deployments.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException(NexusErrorCode.DEPLOYMENT_IN_PROGRESS, "Deployment already in progress");
        }
        entity.applyStatus(DeploymentTransitions.next(entity.getStatus(), DeploymentStatus.RUNNING));
        entity.setStartedAt(Instant.now());
        deployments.save(entity);

        Deployment snapshot = toDomain(entity);
        sseExecutor.execute(() -> runAsync(id, manifest, workingDirectory, username));
        return snapshot;
    }

    private void runAsync(UUID id, ProjectManifest manifest, Path workingDirectory, String username) {
        DeploymentEntity entity = deployments.findById(id).orElseThrow();
        List<String> summaryLines = new ArrayList<>();
        try {
            emit(id, summaryLines, "deployment started");

            List<String> tokens = List.of(manifest.deployment().command().trim().split("\\s+"));
            Path command = workingDirectory.resolve(tokens.getFirst()).normalize();
            if (!command.startsWith(workingDirectory)
                    || !Files.isRegularFile(command)
                    || !Files.isExecutable(command)) {
                emit(id, summaryLines, "Command is not an executable file under the project directory: " + command);
                finish(entity, summaryLines, DeploymentStatus.FAILED, username);
                return;
            }

            List<String> argv = new ArrayList<>();
            argv.add(command.toString());
            argv.addAll(tokens.subList(1, tokens.size()));

            int exit = processExecutor.run(
                    workingDirectory,
                    argv,
                    line -> emit(id, summaryLines, line),
                    SCRIPT_TIMEOUT);
            entity.setExitCode(exit);
            if (exit != 0) {
                finish(entity, summaryLines, DeploymentStatus.FAILED, username);
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
                    finish(entity, summaryLines, DeploymentStatus.SUCCESS, username);
                } else {
                    emit(id, summaryLines, "health check FAILED");
                    finish(entity, summaryLines, DeploymentStatus.FAILED, username);
                }
                return;
            }

            finish(entity, summaryLines, DeploymentStatus.SUCCESS, username);
        } catch (RuntimeException ex) {
            emit(id, summaryLines, "deployment error: " + ex.getMessage());
            finish(entity, summaryLines, DeploymentStatus.FAILED, username);
        }
    }

    private void finish(
            DeploymentEntity entity,
            List<String> summaryLines,
            DeploymentStatus status,
            String username) {
        if (status == DeploymentStatus.SUCCESS) {
            emit(entity.getId(), summaryLines, "DEPLOYMENT SUCCESS");
        } else {
            emit(entity.getId(), summaryLines, "DEPLOYMENT FAILED");
        }
        entity.applyStatus(DeploymentTransitions.next(entity.getStatus(), status));
        entity.setFinishedAt(Instant.now());
        entity.setOutputSummary(summarize(summaryLines));
        deployments.save(entity);
        hub.complete(entity.getId());
        UUID userId = users.findByUsername(username).orElseThrow().getId();
        recordAudit.execute(
                userId,
                AuditAction.DEPLOY,
                entity.getProjectId(),
                null,
                null,
                Map.of("deploymentId", entity.getId().toString(), "status", status.name()));
    }

    private void emit(UUID id, List<String> summaryLines, String line) {
        summaryLines.add(line);
        hub.append(id, line);
    }

    private void upsertProject(ProjectManifest manifest, Path workingDirectory, Path manifestPath) {
        String id = manifest.project().id();
        String name = blankToId(manifest.project().name(), id);
        String description = manifest.project().description();
        String directory = workingDirectory.toString();
        String path = manifestPath.toAbsolutePath().normalize().toString();
        ManagedProjectEntity existing = projects.findById(id).orElse(null);
        if (existing == null) {
            projects.save(new ManagedProjectEntity(id, name, description, directory, path));
            return;
        }
        existing.applyManifest(name, description, directory, path);
        projects.save(existing);
    }

    private static String blankToId(String name, String id) {
        return name == null || name.isBlank() ? id : name;
    }

    private static String summarize(List<String> lines) {
        String joined = String.join("\n", lines);
        if (joined.length() <= SUMMARY_LIMIT) {
            return joined;
        }
        return joined.substring(joined.length() - SUMMARY_LIMIT);
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
}
