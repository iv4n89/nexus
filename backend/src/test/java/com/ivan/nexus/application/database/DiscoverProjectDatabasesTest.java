package com.ivan.nexus.application.database;

import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.application.project.PublishedPort;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoverProjectDatabasesTest {

    @Test
    void discoversReadyLabPostgresAndNexusComposePostgres() {
        DiscoverProjectDatabases discover = new DiscoverProjectDatabases(inventory(
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
    void resolveReturnsTargetForReadyInstance() {
        DiscoverProjectDatabases discover = new DiscoverProjectDatabases(inventory(
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
        DiscoverProjectDatabases discover = new DiscoverProjectDatabases(inventory(
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
        DiscoverProjectDatabases discover = new DiscoverProjectDatabases(inventory());
        DomainException ex = assertThrows(DomainException.class, () -> discover.resolve("lab", "lab:missing"));
        assertEquals(NexusErrorCode.DATABASE_NOT_FOUND, ex.getCode());
    }

    @Test
    void usesContainerIpWhenNoPublishedPort() {
        DiscoverProjectDatabases discover = new DiscoverProjectDatabases(inventory(
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
}
