package com.ivan.nexus.application.site;

import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.EnsureManagedProject;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.application.deployment.ManagedProjectStore;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

        ManagedProjectStore projects = mock(ManagedProjectStore.class);
        EnsureManagedProject ensure = new EnsureManagedProject(projects, manifests, env);
        SyncProjectDomainsFromEnv sync = new SyncProjectDomainsFromEnv(
                env, domains, inventory, manifests, ensure, Optional.empty());

        List<SiteDomain> first = sync.execute("lab");
        assertThat(first).extracting(SiteDomain::hostname)
                .containsExactlyInAnyOrder("from-env.example.com", "from-label.example.com");
        assertThat(first).allMatch(d -> d.serviceName().equals("web"));
        assertThat(first).allMatch(d -> d.targetPort() == 80);
        verify(projects).ensureRegistered("lab", dir.toAbsolutePath().toString(), dir.resolve("nexus.yml").toString());

        List<SiteDomain> second = sync.execute("lab");
        assertThat(second).hasSize(2);
    }

    @Test
    void syncsHostnameFromCaddySnippetWhenEnvHasNoDomain() {
        FakeManifestCatalog manifests = new FakeManifestCatalog();
        FakeProjectDotEnvStore env = new FakeProjectDotEnvStore();
        env.putCaddy("nexus", "0nexus.duckdns.org {\n\treverse_proxy host.docker.internal:3000\n}\n");
        FakeDomainStore domains = new FakeDomainStore();
        ManagedProjectStore projects = mock(ManagedProjectStore.class);
        EnsureManagedProject ensure = new EnsureManagedProject(projects, manifests, env);

        List<SiteDomain> result = new SyncProjectDomainsFromEnv(
                env, domains, emptyInventory(), manifests, ensure, Optional.empty())
                .execute("nexus");

        assertThat(result).extracting(SiteDomain::hostname).containsExactly("0nexus.duckdns.org");
        verify(projects).ensureRegistered("nexus", "nexus", "nexus/nexus.yml");
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

    private static ContainerInventory emptyInventory() {
        return inventory();
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
        private final Map<String, List<String>> caddyByProject = new LinkedHashMap<>();

        @Override
        public Map<String, String> read(String projectId) {
            return Map.copyOf(byProject.getOrDefault(projectId, Map.of()));
        }

        @Override
        public void write(String projectId, Map<String, String> values) {
            byProject.put(projectId, Map.copyOf(values));
        }

        void putCaddy(String projectId, String snippet) {
            caddyByProject.computeIfAbsent(projectId, key -> new java.util.ArrayList<>()).add(snippet);
        }

        @Override
        public List<String> caddySnippets(String projectId) {
            return List.copyOf(caddyByProject.getOrDefault(projectId, List.of()));
        }
    }
}
