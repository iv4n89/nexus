package com.ivan.nexus.application.project;

import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
                snapshot("api", "lab", "running", "healthy")), "/tmp/nexus-no-manifests");

        List<Project> projects = discover.execute();

        assertThat(projects).containsExactly(new Project("lab", "lab", "HEALTHY", 2, 2, false));
    }

    @Test
    void marksDownWhenNoneRunning() {
        DiscoverProjects discover = new DiscoverProjects(inventory(
                snapshot("web", "lab", "exited", null)), "/tmp/nexus-no-manifests");

        assertThat(discover.execute()).containsExactly(new Project("lab", "lab", "DOWN", 0, 1, false));
    }

    @Test
    void marksDegradedWhenMixedOrUnhealthy() {
        DiscoverProjects mixed = new DiscoverProjects(inventory(
                snapshot("web", "lab", "running", null),
                snapshot("api", "lab", "exited", null)), "/tmp/nexus-no-manifests");
        DiscoverProjects unhealthy = new DiscoverProjects(inventory(
                snapshot("web", "lab", "running", "unhealthy")), "/tmp/nexus-no-manifests");

        assertThat(mixed.execute()).containsExactly(new Project("lab", "lab", "DEGRADED", 1, 2, false));
        assertThat(unhealthy.execute()).containsExactly(new Project("lab", "lab", "DEGRADED", 1, 1, false));
    }

    @Test
    void marksDeployableWhenManifestExists(@TempDir Path allowedRoot) throws IOException {
        Path manifest = allowedRoot.resolve("lab").resolve("nexus.yml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "project:\n  id: lab\n");

        DiscoverProjects discover = new DiscoverProjects(inventory(
                snapshot("web", "lab", "running", "healthy")), allowedRoot.toString());

        assertThat(discover.execute()).containsExactly(new Project("lab", "lab", "HEALTHY", 1, 1, true));
    }

    @Test
    void includesManifestOnlyProjectWithNoContainers(@TempDir Path allowedRoot) throws IOException {
        Path manifest = allowedRoot.resolve("solo").resolve("nexus.yml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "project:\n  id: solo\n");

        DiscoverProjects discover = new DiscoverProjects(inventory(), allowedRoot.toString());

        assertThat(discover.execute()).containsExactly(new Project("solo", "solo", "DOWN", 0, 0, true));
        assertThat(discover.groupByProject().get("solo")).isEmpty();
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
