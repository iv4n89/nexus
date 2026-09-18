package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.Deployment;
import com.ivan.nexus.domain.deployment.DeploymentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDeploymentHistoryTest {

    @Test
    void mapsNewestFirstIncludingKindFromDomain() {
        DeploymentStore deployments = mock(DeploymentStore.class);
        UUID newerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID olderId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Deployment newer = deployment(newerId, "lab", DeploymentStatus.SUCCESS, "deploy");
        Deployment older = deployment(olderId, "lab", DeploymentStatus.FAILED, "rollback");
        when(deployments.findProjectHistoryNewestFirst("lab")).thenReturn(List.of(newer, older));

        List<DeploymentView> result = new GetDeploymentHistory(deployments).execute("lab");

        assertThat(result).containsExactly(
                new DeploymentView(
                        newerId,
                        "lab",
                        DeploymentStatus.SUCCESS,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:23Z"),
                        "admin",
                        null,
                        0,
                        "done",
                        true,
                        "deploy"),
                new DeploymentView(
                        olderId,
                        "lab",
                        DeploymentStatus.FAILED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:23Z"),
                        "admin",
                        null,
                        0,
                        "done",
                        true,
                        "rollback"));
    }

    @Test
    void mapsMissingKindAsNull() {
        DeploymentStore deployments = mock(DeploymentStore.class);
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        Deployment deployment = deployment(id, "lab", DeploymentStatus.SUCCESS, null);
        when(deployments.findProjectHistoryNewestFirst("lab")).thenReturn(List.of(deployment));

        List<DeploymentView> result = new GetDeploymentHistory(deployments).execute("lab");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().kind()).isNull();
    }

    private static Deployment deployment(UUID id, String projectId, DeploymentStatus status, String kind) {
        return new Deployment(
                id,
                projectId,
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:23Z"),
                "admin",
                null,
                0,
                "done",
                true,
                kind);
    }
}
