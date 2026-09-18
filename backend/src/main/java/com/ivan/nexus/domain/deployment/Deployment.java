package com.ivan.nexus.domain.deployment;

import java.time.Instant;
import java.util.UUID;

public record Deployment(
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
}
