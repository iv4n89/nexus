package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;

import java.time.Instant;
import java.util.UUID;

public record DeploymentView(
        UUID id,
        String projectId,
        DeploymentStatus status,
        Instant startedAt,
        Instant finishedAt,
        String triggeredBy,
        String commitSha,
        Integer exitCode,
        String outputSummary,
        Boolean healthOk,
        String kind) {

    static DeploymentView from(DeploymentEntity entity) {
        return new DeploymentView(
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
