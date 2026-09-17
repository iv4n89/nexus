package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.deployment.DeploymentTransitions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FakeDeploymentStore implements DeploymentStore {
    final Map<UUID, Deployment> deployments = new LinkedHashMap<>();
    final List<String> calls = new ArrayList<>();

    @Override
    public boolean hasActiveDeployment(String projectId) {
        calls.add("active:" + projectId);
        return deployments.values().stream()
                .anyMatch(deployment -> deployment.projectId().equals(projectId)
                        && (deployment.status() == DeploymentStatus.PENDING
                        || deployment.status() == DeploymentStatus.RUNNING));
    }

    @Override
    public Deployment createPending(UUID id, String projectId, String triggeredBy, String kind) {
        calls.add("pending:" + id);
        Deployment deployment = new Deployment(
                id, projectId, DeploymentStatus.PENDING, null, null, triggeredBy,
                null, null, null, null, kind);
        deployments.put(id, deployment);
        return deployment;
    }

    @Override
    public Deployment markRunning(UUID id, Instant startedAt) {
        calls.add("running:" + id);
        Deployment current = required(id);
        return replace(current, DeploymentTransitions.next(current.status(), DeploymentStatus.RUNNING),
                startedAt, current.finishedAt(), current.exitCode(), current.outputSummary(), current.healthOk());
    }

    @Override
    public Deployment recordExitCode(UUID id, int exitCode) {
        calls.add("exit:" + exitCode);
        Deployment current = required(id);
        return replace(current, current.status(), current.startedAt(), current.finishedAt(),
                exitCode, current.outputSummary(), current.healthOk());
    }

    @Override
    public Deployment recordHealthResult(UUID id, boolean healthOk) {
        calls.add("health:" + healthOk);
        Deployment current = required(id);
        return replace(current, current.status(), current.startedAt(), current.finishedAt(),
                current.exitCode(), current.outputSummary(), healthOk);
    }

    @Override
    public Deployment finish(
            UUID id, DeploymentStatus terminalStatus, Instant finishedAt, String outputSummary) {
        calls.add("finish:" + terminalStatus);
        Deployment current = required(id);
        return replace(current, DeploymentTransitions.next(current.status(), terminalStatus),
                current.startedAt(), finishedAt, current.exitCode(), outputSummary, current.healthOk());
    }

    @Override
    public Optional<Deployment> findById(UUID id) {
        calls.add("find:" + id);
        return Optional.ofNullable(deployments.get(id));
    }

    @Override
    public List<Deployment> findProjectHistoryNewestFirst(String projectId) {
        return deployments.values().stream()
                .filter(deployment -> deployment.projectId().equals(projectId))
                .sorted(Comparator.comparing(Deployment::startedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private Deployment required(UUID id) {
        return Optional.ofNullable(deployments.get(id)).orElseThrow();
    }

    private Deployment replace(
            Deployment current,
            DeploymentStatus status,
            Instant startedAt,
            Instant finishedAt,
            Integer exitCode,
            String outputSummary,
            Boolean healthOk) {
        Deployment updated = new Deployment(
                current.id(),
                current.projectId(),
                status,
                startedAt,
                finishedAt,
                current.triggeredBy(),
                current.commitSha(),
                exitCode,
                outputSummary,
                healthOk,
                current.kind());
        deployments.put(updated.id(), updated);
        return updated;
    }
}
