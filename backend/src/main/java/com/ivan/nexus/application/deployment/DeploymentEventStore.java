package com.ivan.nexus.application.deployment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeploymentEventStore {
    void append(UUID deploymentId, String line);

    List<String> findLinesOldestFirst(UUID deploymentId);

    long deleteCreatedBefore(Instant cutoff);
}
