package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDeploymentTest {

    @Test
    void returnsViewIncludingKindFromMetadata() {
        DeploymentJpaRepository deployments = mock(DeploymentJpaRepository.class);
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        DeploymentEntity entity = new DeploymentEntity(
                id,
                "lab",
                DeploymentStatus.SUCCESS,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:23Z"),
                "admin",
                "abc123",
                0,
                "done",
                true);
        entity.setMetadata(Map.of("kind", "deploy"));
        when(deployments.findByIdAndProjectId(id, "lab")).thenReturn(Optional.of(entity));

        DeploymentView result = new GetDeployment(deployments).execute("lab", id);

        assertThat(result).isEqualTo(new DeploymentView(
                id,
                "lab",
                DeploymentStatus.SUCCESS,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:23Z"),
                "admin",
                "abc123",
                0,
                "done",
                true,
                "deploy"));
    }

    @Test
    void unknownDeploymentIsNotFound() {
        DeploymentJpaRepository deployments = mock(DeploymentJpaRepository.class);
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(deployments.findByIdAndProjectId(id, "lab")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetDeployment(deployments).execute("lab", id))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.DEPLOYMENT_NOT_FOUND);
    }
}
