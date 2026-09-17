package com.ivan.nexus.infrastructure.sse;

import com.ivan.nexus.application.deployment.DeploymentProgress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentStreamHubTest {
    @Test
    void implementsApplicationDeploymentProgressPort() {
        assertThat(DeploymentStreamHub.class).isAssignableTo(DeploymentProgress.class);
    }
}
