package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequireDeploymentTest {

    @Test
    void unknownDeploymentIsNotFound() {
        DeploymentJpaRepository deployments = mock(DeploymentJpaRepository.class);
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(deployments.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> new RequireDeployment(deployments).execute(id))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.DEPLOYMENT_NOT_FOUND);
    }

    @Test
    void existingDeploymentDoesNotThrow() {
        DeploymentJpaRepository deployments = mock(DeploymentJpaRepository.class);
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(deployments.existsById(id)).thenReturn(true);

        assertThatCode(() -> new RequireDeployment(deployments).execute(id)).doesNotThrowAnyException();
    }
}
