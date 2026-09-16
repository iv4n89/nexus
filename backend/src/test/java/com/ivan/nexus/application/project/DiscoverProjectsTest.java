package com.ivan.nexus.application.project;

import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoverProjectsTest {

    @Test
    void groupsByNexusProjectAndMarksHealthyWhenAllRunning() {
        DiscoverProjects discover = new DiscoverProjects(inventory(
                snapshot("web", "lab", "running", null),
                snapshot("api", "lab", "running", "healthy")));

        List<Project> projects = discover.execute();

        assertThat(projects).containsExactly(new Project("lab", "lab", "HEALTHY", 2, 2));
    }

    @Test
    void marksDownWhenNoneRunning() {
        DiscoverProjects discover = new DiscoverProjects(inventory(
                snapshot("web", "lab", "exited", null)));

        assertThat(discover.execute()).containsExactly(new Project("lab", "lab", "DOWN", 0, 1));
    }

    @Test
    void marksDegradedWhenMixedOrUnhealthy() {
        DiscoverProjects mixed = new DiscoverProjects(inventory(
                snapshot("web", "lab", "running", null),
                snapshot("api", "lab", "exited", null)));
        DiscoverProjects unhealthy = new DiscoverProjects(inventory(
                snapshot("web", "lab", "running", "unhealthy")));

        assertThat(mixed.execute()).containsExactly(new Project("lab", "lab", "DEGRADED", 1, 2));
        assertThat(unhealthy.execute()).containsExactly(new Project("lab", "lab", "DEGRADED", 1, 1));
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

    private static ContainerSnapshot snapshot(String service, String project, String state, String health) {
        return new ContainerSnapshot(
                service + "-id",
                "lab-" + service + "-1",
                "nginx:alpine",
                "Up",
                state,
                health,
                Instant.parse("2026-01-01T00:00:00Z"),
                Map.of("nexus.project", project, "nexus.service", service),
                List.of(),
                0,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
