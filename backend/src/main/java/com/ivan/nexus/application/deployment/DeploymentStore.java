package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeploymentStore {
    boolean hasActiveDeployment(String projectId);

    Deployment createPending(UUID id, String projectId, String triggeredBy, String kind, String commitSha);

    Deployment markRunning(UUID id, Instant startedAt);

    Deployment recordExitCode(UUID id, int exitCode);

    Deployment recordHealthResult(UUID id, boolean healthOk);

    Deployment finish(UUID id, DeploymentStatus terminalStatus, Instant finishedAt, String outputSummary);

    Optional<Deployment> findById(UUID id);

    List<Deployment> findProjectHistoryNewestFirst(String projectId);
}
