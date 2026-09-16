package com.ivan.nexus.application.metrics;

import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.DiscoverProjects;
import com.ivan.nexus.application.project.GetProject;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.metrics.ContainerMetrics;
import com.ivan.nexus.domain.metrics.ProjectMetrics;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProjectMetricsTest {

    @Test
    void sumsCpuMemoryAndRestartsForRunningContainers() {
        GetProjectMetrics useCase = useCase(
                id -> switch (id) {
                    case "web-id" -> new ContainerMetrics("web-id", 10.0, 100, 0, 0, 0);
                    case "api-id" -> new ContainerMetrics("api-id", 5.5, 50, 0, 0, 0);
                    default -> throw new IllegalStateException("unexpected " + id);
                },
                snapshot("web-id", "running", 1),
                snapshot("api-id", "running", 3),
                snapshot("db-id", "exited", 9));

        assertThat(useCase.execute("lab")).isEqualTo(new ProjectMetrics("lab", 15.5, 150, 4));
    }

    @Test
    void skipsCpuAndMemoryWhenStatsFailButStillCountsRestarts() {
        GetProjectMetrics useCase = useCase(
                id -> {
                    if ("api-id".equals(id)) {
                        throw new RuntimeException("stats failed");
                    }
                    return new ContainerMetrics(id, 8.0, 80, 0, 0, 0);
                },
                snapshot("web-id", "running", 2),
                snapshot("api-id", "running", 4));

        assertThat(useCase.execute("lab")).isEqualTo(new ProjectMetrics("lab", 8.0, 80, 6));
    }

    @Test
    void missingProjectThrows() {
        GetProjectMetrics useCase = useCase(id -> new ContainerMetrics(id, 0, 0, 0, 0, 0));

        assertThatThrownBy(() -> useCase.execute("missing"))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.PROJECT_NOT_FOUND));
    }

    private static GetProjectMetrics useCase(ContainerStatsProvider stats, ContainerSnapshot... snapshots) {
        return new GetProjectMetrics(new GetProject(new DiscoverProjects(inventory(snapshots), "/tmp/nexus-no-manifests")), stats);
    }

    private static ContainerInventory inventory(ContainerSnapshot... snapshots) {
        List<ContainerSnapshot> all = List.of(snapshots);
        return new ContainerInventory() {
            @Override
            public List<ContainerSnapshot> listAll() {
                return all;
            }

            @Override
            public Optional<ContainerSnapshot> findById(String containerId) {
                return all.stream().filter(snapshot -> snapshot.id().equals(containerId)).findFirst();
            }
        };
    }

    private static ContainerSnapshot snapshot(String id, String state, int restartCount) {
        return new ContainerSnapshot(
                id,
                "lab-" + id,
                "nginx:alpine",
                "Up",
                state,
                "healthy",
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", "lab"),
                List.of(),
                restartCount,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
