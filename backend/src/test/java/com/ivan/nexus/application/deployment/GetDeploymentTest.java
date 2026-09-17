package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDeploymentTest {

    @Test
    void returnsViewIncludingKindFromDomain() {
        DeploymentStore deployments = mock(DeploymentStore.class);
        UUID id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Deployment deployment = new Deployment(
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
                "deploy");
        when(deployments.findById(id)).thenReturn(Optional.of(deployment));

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
        DeploymentStore deployments = mock(DeploymentStore.class);
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(deployments.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetDeployment(deployments).execute("lab", id))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.DEPLOYMENT_NOT_FOUND);
    }

    @Test
    void deploymentFromAnotherProjectIsNotFound() {
        DeploymentStore deployments = mock(DeploymentStore.class);
        UUID id = UUID.randomUUID();
        Deployment deployment = new Deployment(
                id, "other", DeploymentStatus.RUNNING, Instant.now(), null,
                "admin", null, null, null, null, "deploy");
        when(deployments.findById(id)).thenReturn(Optional.of(deployment));

        assertThatThrownBy(() -> new GetDeployment(deployments).execute("lab", id))
                .isInstanceOf(DomainException.class)
                .hasMessage("Deployment not found");
    }
}
