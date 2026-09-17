package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;

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

    static DeploymentView from(Deployment deployment) {
        return new DeploymentView(
                deployment.id(),
                deployment.projectId(),
                deployment.status(),
                deployment.startedAt(),
                deployment.finishedAt(),
                deployment.triggeredBy(),
                deployment.commitSha(),
                deployment.exitCode(),
                deployment.outputSummary(),
                deployment.healthOk(),
                deployment.kind());
    }
}
