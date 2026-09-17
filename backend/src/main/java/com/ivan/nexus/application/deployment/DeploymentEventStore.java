package com.ivan.nexus.application.deployment;

import java.time.Instant;

public interface DeploymentEventStore {
    long deleteCreatedBefore(Instant cutoff);
}
