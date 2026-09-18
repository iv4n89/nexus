package com.ivan.nexus.application.database;

import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscoverProjectDatabasesTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversReadyLabPostgresAndNexusComposePostgres() {
        DiscoverProjectDatabases discover = discover(inventory(
                inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of("POSTGRES_USER", "lab", "POSTGRES_PASSWORD", "lab", "POSTGRES_DB", "lab"),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of()),
                inspect(
                        "bbbbbbbbbbbb0000",
                        "lab-web-1",
                        "nginx:alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "web"),
                        Map.of(),
                        List.of(new PublishedPort(80, 18081, "0.0.0.0")),
                        List.of()),
                inspect(
                        "cccccccccccc0000",
                        "nexus-postgres-1",
                        "postgres:16-alpine",
                        Map.of(
                                "nexus.project", "nexus",
                                "com.docker.compose.project", "nexus",
                                "com.docker.compose.service", "postgres"),
                        Map.of("POSTGRES_PASSWORD", "nexus", "POSTGRES_DB", "nexus", "POSTGRES_USER", "nexus"),
                        List.of(new PublishedPort(5432, 5432, "127.0.0.1")),
                        List.of())));

        List<DatabaseInstance> instances = discover.execute("lab");

        assertEquals(1, instances.size());
        DatabaseInstance db = instances.getFirst();
        assertEquals("lab:aaaaaaaaaaaa", db.id());
        assertEquals("db", db.service());
        assertEquals(DatabaseEngine.POSTGRES, db.engine());
        assertEquals(DatabaseStatus.READY, db.status());
        assertEquals("lab", db.defaultDatabase());

        List<DatabaseInstance> nexus = discover.execute("nexus");
        assertEquals(1, nexus.size());
        assertEquals("postgres", nexus.getFirst().service());
        assertEquals(DatabaseEngine.POSTGRES, nexus.getFirst().engine());
        assertEquals(DatabaseStatus.READY, nexus.getFirst().status());
    }

    @Test
    void matchesNormalizedComposeProjectId() {
        Path projectDir = tempDir.resolve("foo-bar");
        FakeManifestCatalog manifests = new FakeManifestCatalog()
                .add("foo-bar", manifest("foo-bar", projectDir), projectDir.resolve("nexus.yml"));
        FakeDotEnvStore env = new FakeDotEnvStore();
        DiscoverProjectDatabases discover = new DiscoverProjectDatabases(
                inventory(inspect(
                        "aaaaaaaaaaaa0000",
                        "foo_bar-db-1",
                        "postgres:16-alpine",
                        Map.of(
                                "com.docker.compose.project", "foo_bar",
                                "com.docker.compose.service", "db"),
                        Map.of("POSTGRES_PASSWORD", "secret", "POSTGRES_DB", "app"),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of())),
                env,
                manifests);

        List<DatabaseInstance> instances = discover.execute("foo-bar");
        assertEquals(1, instances.size());
        assertEquals(DatabaseStatus.READY, instances.getFirst().status());
    }

    @Test
    void fillsPasswordFromProjectDotEnv() {
        FakeDotEnvStore env = new FakeDotEnvStore();
        env.write("lab", Map.of("POSTGRES_PASSWORD", "from-dotenv", "POSTGRES_DB", "lab"));
        DiscoverProjectDatabases discover = discover(
                inventory(inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of("POSTGRES_USER", "lab", "POSTGRES_DB", "lab"),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of())),
                env);

        InstanceResolution resolution = discover.resolve("lab", "lab:aaaaaaaaaaaa");
        assertEquals(DatabaseStatus.READY, resolution.instance().status());
        assertEquals("from-dotenv", resolution.target().password());
    }

    @Test
    void parsesDatabaseUrlFromDotEnv() {
        FakeDotEnvStore env = new FakeDotEnvStore();
        env.write("lab", Map.of(
                "DATABASE_URL", "postgres://app:s3cret@db.internal:5432/appdb"));
        DiscoverProjectDatabases discover = discover(
                inventory(inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of(),
                        List.of(),
                        List.of())),
                env);

        InstanceResolution resolution = discover.resolve("lab", "lab:aaaaaaaaaaaa");
        assertEquals(DatabaseStatus.READY, resolution.instance().status());
        assertEquals("db.internal", resolution.target().host());
        assertEquals(5432, resolution.target().port());
        assertEquals("app", resolution.target().username());
        assertEquals("s3cret", resolution.target().password());
        assertEquals("appdb", resolution.target().defaultDatabase());
    }

    @Test
    void resolveReturnsTargetForReadyInstance() {
        DiscoverProjectDatabases discover = discover(inventory(
                inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of("POSTGRES_USER", "lab", "POSTGRES_PASSWORD", "secret", "POSTGRES_DB", "lab"),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of())));

        InstanceResolution resolution = discover.resolve("lab", "lab:aaaaaaaaaaaa");
        assertEquals("127.0.0.1", resolution.target().host());
        assertEquals(15432, resolution.target().port());
        assertEquals("secret", resolution.target().password());
    }

    @Test
    void missingPasswordIsUnreachableWithNullTarget() {
        DiscoverProjectDatabases discover = discover(inventory(
                inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of("POSTGRES_DB", "lab"),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of())));

        DatabaseInstance instance = discover.execute("lab").getFirst();
        assertEquals(DatabaseStatus.UNREACHABLE, instance.status());
        assertNull(discover.resolve("lab", instance.id()).target());
    }

    @Test
    void unknownDatabaseThrowsNotFound() {
        DiscoverProjectDatabases discover = discover(inventory());
        DomainException ex = assertThrows(DomainException.class, () -> discover.resolve("lab", "lab:missing"));
        assertEquals(NexusErrorCode.DATABASE_NOT_FOUND, ex.getCode());
    }

    @Test
    void inspectsOnlyDatabaseCandidatesNotEveryProjectContainer() {
        CountingInventory inventory = countingInventory(
                inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of("POSTGRES_PASSWORD", "p", "POSTGRES_DB", "lab"),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of()),
                inspect(
                        "bbbbbbbbbbbb0000",
                        "lab-web-1",
                        "nginx:alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "web"),
                        Map.of(),
                        List.of(new PublishedPort(80, 18081, "0.0.0.0")),
                        List.of()));

        DiscoverProjectDatabases discover = discover(inventory);
        List<DatabaseInstance> instances = discover.execute("lab");

        assertEquals(1, instances.size());
        assertEquals(List.of("aaaaaaaaaaaa0000"), inventory.inspectedIds);
        assertEquals(1, inventory.listAllCalls.get());
    }

    @Test
    void resolveReusesRecentDiscoveryInsteadOfInspectingAgain() {
        CountingInventory inventory = countingInventory(
                inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of("POSTGRES_PASSWORD", "p", "POSTGRES_DB", "lab"),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of()));
        DiscoverProjectDatabases discover = discover(inventory);

        discover.resolve("lab", "lab:aaaaaaaaaaaa");
        discover.resolve("lab", "lab:aaaaaaaaaaaa");

        assertEquals(1, inventory.listAllCalls.get());
        assertEquals(1, inventory.inspectedIds.size());
    }

    @Test
    void usesContainerIpWhenNoPublishedPort() {
        DiscoverProjectDatabases discover = discover(inventory(
                inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of("POSTGRES_PASSWORD", "p", "POSTGRES_DB", "lab"),
                        List.of(),
                        List.of("172.18.0.2"))));

        InstanceResolution resolution = discover.resolve("lab", "lab:aaaaaaaaaaaa");
        assertEquals("172.18.0.2", resolution.target().host());
        assertEquals(5432, resolution.target().port());
        assertEquals(DatabaseStatus.READY, resolution.instance().status());
    }

    @Test
    void prefersPublishedLocalhostOverDotEnvHost() {
        FakeDotEnvStore env = new FakeDotEnvStore();
        env.write("lab", Map.of(
                "POSTGRES_PASSWORD", "p",
                "DB_HOST", "db.example.com"));
        DiscoverProjectDatabases discover = discover(
                inventory(inspect(
                        "aaaaaaaaaaaa0000",
                        "lab-db-1",
                        "postgres:16-alpine",
                        Map.of("nexus.project", "lab", "nexus.service", "db"),
                        Map.of(),
                        List.of(new PublishedPort(5432, 15432, "0.0.0.0")),
                        List.of())),
                env);

        InstanceResolution resolution = discover.resolve("lab", "lab:aaaaaaaaaaaa");
        assertEquals("127.0.0.1", resolution.target().host());
        assertEquals(15432, resolution.target().port());
    }

    private DiscoverProjectDatabases discover(ContainerInventory inventory) {
        return discover(inventory, new FakeDotEnvStore());
    }

    private DiscoverProjectDatabases discover(ContainerInventory inventory, ProjectDotEnvStore env) {
        return new DiscoverProjectDatabases(inventory, env, new FakeManifestCatalog());
    }

    private static ProjectManifest manifest(String id, Path workingDirectory) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock(
                        id, id, null, workingDirectory.toAbsolutePath().toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
    }

    private static CountingInventory countingInventory(ContainerInspect... inspects) {
        return new CountingInventory(inspects);
    }

    private static final class CountingInventory implements ContainerInventory {
        private final List<ContainerInspect> all;
        private final List<ContainerSnapshot> snapshots;
        private final AtomicInteger listAllCalls = new AtomicInteger();
        private final List<String> inspectedIds = new ArrayList<>();

        private CountingInventory(ContainerInspect... inspects) {
            this.all = List.of(inspects);
            this.snapshots = Stream.of(inspects).map(DiscoverProjectDatabasesTest::snapshot).toList();
        }

        @Override
        public List<ContainerSnapshot> listAll() {
            listAllCalls.incrementAndGet();
            return snapshots;
        }

        @Override
        public Optional<ContainerSnapshot> findById(String containerId) {
            return snapshots.stream().filter(s -> s.id().equals(containerId)).findFirst();
        }

        @Override
        public Optional<ContainerInspect> inspect(String containerId) {
            inspectedIds.add(containerId);
            return all.stream().filter(item -> item.id().equals(containerId)).findFirst();
        }
    }

    private static ContainerInventory inventory(ContainerInspect... inspects) {
        List<ContainerInspect> all = List.of(inspects);
        List<ContainerSnapshot> snapshots = Stream.of(inspects).map(DiscoverProjectDatabasesTest::snapshot).toList();
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

    static final class FakeDotEnvStore implements ProjectDotEnvStore {
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
