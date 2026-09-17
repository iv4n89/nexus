package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.deployment.DeploymentStatus;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentEntity;
import com.ivan.nexus.infrastructure.persistence.deployment.DeploymentJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDeploymentHistoryTest {

    @Test
    void mapsNewestFirstIncludingKindFromMetadata() {
        DeploymentJpaRepository deployments = mock(DeploymentJpaRepository.class);
        UUID newerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID olderId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        DeploymentEntity newer = entity(newerId, "lab", DeploymentStatus.SUCCESS, "deploy");
        DeploymentEntity older = entity(olderId, "lab", DeploymentStatus.FAILED, "rollback");
        when(deployments.findByProjectIdOrderByCreatedAtDesc("lab")).thenReturn(List.of(newer, older));

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
        DeploymentJpaRepository deployments = mock(DeploymentJpaRepository.class);
        UUID id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        DeploymentEntity entity = entity(id, "lab", DeploymentStatus.SUCCESS, null);
        when(deployments.findByProjectIdOrderByCreatedAtDesc("lab")).thenReturn(List.of(entity));

        List<DeploymentView> result = new GetDeploymentHistory(deployments).execute("lab");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().kind()).isNull();
    }

    private static DeploymentEntity entity(UUID id, String projectId, DeploymentStatus status, String kind) {
        DeploymentEntity entity = new DeploymentEntity(
                id,
                projectId,
                status,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:23Z"),
                "admin",
                null,
                0,
                "done",
                true);
        if (kind != null) {
            entity.setMetadata(Map.of("kind", kind));
        }
        return entity;
    }
}
