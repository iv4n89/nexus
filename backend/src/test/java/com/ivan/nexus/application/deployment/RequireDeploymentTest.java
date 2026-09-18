package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequireDeploymentTest {

    @Test
    void unknownDeploymentIsNotFound() {
        DeploymentStore deployments = mock(DeploymentStore.class);
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(deployments.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RequireDeployment(deployments).execute(id))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.DEPLOYMENT_NOT_FOUND);
    }

    @Test
    void existingDeploymentDoesNotThrow() {
        DeploymentStore deployments = mock(DeploymentStore.class);
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(deployments.findById(id)).thenReturn(Optional.of(new Deployment(
                id, "lab", DeploymentStatus.RUNNING, Instant.now(), null,
                "admin", null, null, null, null, "deploy")));

        assertThatCode(() -> new RequireDeployment(deployments).execute(id)).doesNotThrowAnyException();
    }
}
