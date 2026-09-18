package com.ivan.nexus.infrastructure.persistence.deployment;

import com.ivan.nexus.application.deployment.DeploymentStore;
import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.deployment.DeploymentTransitions;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaDeploymentStore implements DeploymentStore {
    private static final String ACTIVE_INDEX = "uq_deployments_running";
    private static final List<DeploymentStatus> ACTIVE_STATUSES =
            List.of(DeploymentStatus.PENDING, DeploymentStatus.RUNNING);

    private final DeploymentJpaRepository repository;

    JpaDeploymentStore(DeploymentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean hasActiveDeployment(String projectId) {
        return repository.existsByProjectIdAndStatusIn(projectId, ACTIVE_STATUSES);
    }

    @Override
    public Deployment createPending(UUID id, String projectId, String triggeredBy, String kind, String commitSha) {
        DeploymentEntity entity = new DeploymentEntity(
                id,
                projectId,
                DeploymentStatus.PENDING,
                null,
                null,
                triggeredBy,
                commitSha,
                null,
                null,
                null,
                Map.of("kind", kind));
        try {
            return toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (!isActiveIndexViolation(ex)) {
                throw ex;
            }
            throw new DomainException(
                    NexusErrorCode.DEPLOYMENT_IN_PROGRESS,
                    "Deployment already in progress");
        }
    }

    @Override
    public Deployment markRunning(UUID id, Instant startedAt) {
        DeploymentEntity entity = load(id);
        entity.applyStatus(DeploymentTransitions.next(entity.getStatus(), DeploymentStatus.RUNNING));
        entity.setStartedAt(startedAt);
        return toDomain(repository.save(entity));
    }

    @Override
    public Deployment recordExitCode(UUID id, int exitCode) {
        DeploymentEntity entity = load(id);
        entity.setExitCode(exitCode);
        return toDomain(repository.save(entity));
    }

    @Override
    public Deployment recordHealthResult(UUID id, boolean healthOk) {
        DeploymentEntity entity = load(id);
        entity.setHealthOk(healthOk);
        return toDomain(repository.save(entity));
    }

    @Override
    public Deployment finish(
            UUID id,
            DeploymentStatus terminalStatus,
            Instant finishedAt,
            String outputSummary) {
        DeploymentEntity entity = load(id);
        entity.applyStatus(DeploymentTransitions.next(entity.getStatus(), terminalStatus));
        entity.setFinishedAt(finishedAt);
        entity.setOutputSummary(outputSummary);
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Deployment> findById(UUID id) {
        return repository.findById(id).map(JpaDeploymentStore::toDomain);
    }

    @Override
    public List<Deployment> findProjectHistoryNewestFirst(String projectId) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(JpaDeploymentStore::toDomain)
                .toList();
    }

    private DeploymentEntity load(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    private static boolean isActiveIndexViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains(ACTIVE_INDEX)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
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
                entity.getHealthOk(),
                entity.getKind());
    }
}
