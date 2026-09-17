package com.ivan.nexus.application.deployment;

import java.util.UUID;

public interface DeploymentProgress {
    void append(UUID deploymentId, String line);

    void complete(UUID deploymentId);
}
