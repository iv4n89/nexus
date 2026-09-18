package com.ivan.nexus.application.site;

import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.site.SiteDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SyncProjectDomainsFromEnvTest {

    @TempDir
    Path tempDir;

    @Test
    void syncsFromDotEnvAndLabelsWithoutDuplicating() {
        Path dir = tempDir.resolve("lab");
        FakeManifestCatalog manifests = new FakeManifestCatalog()
                .add("lab", manifest(dir), dir.resolve("nexus.yml"));
        FakeProjectDotEnvStore env = new FakeProjectDotEnvStore();
        env.write("lab", Map.of("DOMAIN", "from-env.example.com"));
        FakeDomainStore domains = new FakeDomainStore();
        ContainerInventory inventory = inventory(inspect(
                "web1",
                "lab-web-1",
                "nginx:alpine",
                Map.of(
                        "nexus.project", "lab",
                        "nexus.service", "web",
                        "caddy", "from-label.example.com"),
                Map.of(),
                List.of(new PublishedPort(80, 8080, "0.0.0.0")),
                List.of()));

        SyncProjectDomainsFromEnv sync = new SyncProjectDomainsFromEnv(
                env, domains, inventory, manifests, Optional.empty());

        List<SiteDomain> first = sync.execute("lab");
        assertThat(first).extracting(SiteDomain::hostname)
                .containsExactlyInAnyOrder("from-env.example.com", "from-label.example.com");
        assertThat(first).allMatch(d -> d.serviceName().equals("web"));
        assertThat(first).allMatch(d -> d.targetPort() == 80);

        List<SiteDomain> second = sync.execute("lab");
        assertThat(second).hasSize(2);
    }

    private static ProjectManifest manifest(Path workingDirectory) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock(
                        "lab", "Lab", null, workingDirectory.toAbsolutePath().toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
    }

    private static ContainerInventory inventory(ContainerInspect... inspects) {
        List<ContainerInspect> all = List.of(inspects);
        List<ContainerSnapshot> snapshots = java.util.stream.Stream.of(inspects)
                .map(SyncProjectDomainsFromEnvTest::snapshot)
                .toList();
        return new ContainerInventory() {
            @Override
            public List<ContainerSnapshot> listAll() {
                return snapshots;
            }

            @Override
            public Optional<ContainerSnapshot> findById(String containerId) {
                return snapshots.stream().filter(s -> s.id().equals(containerId)).findFirst();
            }

            @Override
            public Optional<ContainerInspect> inspect(String containerId) {
                return all.stream().filter(item -> item.id().equals(containerId)).findFirst();
            }
        };
    }

    private static ContainerInspect inspect(
            String id,
            String name,
            String image,
            Map<String, String> labels,
            Map<String, String> env,
            List<PublishedPort> ports,
            List<String> ips) {
        return new ContainerInspect(id, name, image, labels, env, ports, ips);
    }

    private static ContainerSnapshot snapshot(ContainerInspect inspect) {
        return new ContainerSnapshot(
                inspect.id(),
                inspect.name(),
                inspect.image(),
                "Up",
                "running",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                inspect.labels(),
                List.of(),
                0,
                Instant.parse("2026-01-01T00:00:01Z"));
    }

    static final class FakeProjectDotEnvStore implements ProjectDotEnvStore {
        private final Map<String, Map<String, String>> byProject = new LinkedHashMap<>();

        @Override
        public Map<String, String> read(String projectId) {
            return Map.copyOf(byProject.getOrDefault(projectId, Map.of()));
        }

        @Override
        public void write(String projectId, Map<String, String> values) {
            byProject.put(projectId, Map.copyOf(values));
        }
    }
}
