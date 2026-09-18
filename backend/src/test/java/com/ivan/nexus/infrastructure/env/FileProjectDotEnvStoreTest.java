package com.ivan.nexus.infrastructure.env;

import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.application.project.ContainerInspect;
import com.ivan.nexus.application.project.ContainerInventory;
import com.ivan.nexus.domain.container.ContainerSnapshot;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileProjectDotEnvStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void readWriteRoundTripPreservesOrder() throws Exception {
        Path allowedRoot = tempDir.resolve("projects");
        Path projectDir = allowedRoot.resolve("lab");
        Files.createDirectories(projectDir);
        FakeManifestCatalog manifests = new FakeManifestCatalog()
                .add("lab", manifest("lab", projectDir), projectDir.resolve("nexus.yml"));
        FileProjectDotEnvStore store = new FileProjectDotEnvStore(
                manifests, emptyInventory(), allowedRoot, List.of(), null);

        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("NODE_ENV", "production");
        values.put("API_KEY", "secret");
        store.write("lab", values);

        assertThat(Files.readString(projectDir.resolve(".env")))
                .isEqualTo("NODE_ENV=production\nAPI_KEY=secret\n");
        assertThat(store.read("lab")).containsExactly(
                Map.entry("NODE_ENV", "production"),
                Map.entry("API_KEY", "secret"));
    }

    @Test
    void readsDotEnvFromComposeWorkingDirUnderExtraRoot() throws Exception {
        Path projects = tempDir.resolve("srv-projects");
        Path hostOpt = tempDir.resolve("host-opt");
        Path nexusDir = hostOpt.resolve("nexus");
        Files.createDirectories(projects);
        Files.createDirectories(nexusDir);
        Files.writeString(nexusDir.resolve(".env"), "POSTGRES_PASSWORD=from-opt\nDOMAIN=0nexus.example.com\n");

        ContainerInventory inventory = inventory(snapshot(
                "pg1",
                "nexus-postgres-1",
                Map.of(
                        "com.docker.compose.project", "nexus",
                        "com.docker.compose.project.working_dir", nexusDir.toString(),
                        "com.docker.compose.service", "postgres")));

        FileProjectDotEnvStore store = new FileProjectDotEnvStore(
                new FakeManifestCatalog(),
                inventory,
                projects,
                List.of(hostOpt.toString()),
                hostOpt.toString());

        assertThat(store.read("nexus"))
                .containsEntry("POSTGRES_PASSWORD", "from-opt")
                .containsEntry("DOMAIN", "0nexus.example.com");
        assertThat(store.resolveEnvPath("nexus"))
                .isEqualTo(nexusDir.resolve(".env").toAbsolutePath().normalize());
    }

    @Test
    void remapsHostOptPathToContainerMount() throws Exception {
        Path projects = tempDir.resolve("projects");
        Path hostOpt = tempDir.resolve("host-opt");
        Path nexusOnMount = hostOpt.resolve("nexus");
        Files.createDirectories(projects);
        Files.createDirectories(nexusOnMount);

        FileProjectDotEnvStore store = new FileProjectDotEnvStore(
                new FakeManifestCatalog(),
                emptyInventory(),
                projects,
                List.of(hostOpt.toAbsolutePath().toString()),
                hostOpt.toAbsolutePath().toString());

        assertThat(store.usableProjectDir(Path.of("/opt/nexus")))
                .isEqualTo(nexusOnMount.toAbsolutePath().normalize());
    }

    @Test
    void fallsBackToExtraRootDirectoryWhenComposeLabelMissing() throws Exception {
        Path projects = tempDir.resolve("srv-projects");
        Path hostOpt = tempDir.resolve("host-opt");
        Path nexusDir = hostOpt.resolve("nexus");
        Files.createDirectories(projects);
        Files.createDirectories(nexusDir);
        Files.writeString(nexusDir.resolve(".env"), "POSTGRES_PASSWORD=from-host-opt\n");

        FileProjectDotEnvStore store = new FileProjectDotEnvStore(
                new FakeManifestCatalog(),
                emptyInventory(),
                projects,
                List.of(hostOpt.toString()),
                hostOpt.toString());

        assertThat(store.read("nexus")).containsEntry("POSTGRES_PASSWORD", "from-host-opt");
        assertThat(store.locateDirectory("nexus")).contains(nexusDir.toAbsolutePath().normalize());
    }

    @Test
    void readsCaddySnippetsFromProjectDir() throws Exception {
        Path projects = tempDir.resolve("srv-projects");
        Path hostOpt = tempDir.resolve("host-opt");
        Path nexusDir = hostOpt.resolve("nexus");
        Path caddyDir = nexusDir.resolve("caddy");
        Files.createDirectories(projects);
        Files.createDirectories(caddyDir);
        Files.writeString(caddyDir.resolve("nexus.caddy"), "0nexus.duckdns.org {\n}\n");

        FileProjectDotEnvStore store = new FileProjectDotEnvStore(
                new FakeManifestCatalog(),
                emptyInventory(),
                projects,
                List.of(hostOpt.toString()),
                hostOpt.toString());

        assertThat(store.caddySnippets("nexus")).anyMatch(snippet -> snippet.contains("0nexus.duckdns.org"));
    }

    @Test
    void readReturnsEmptyWhenPathOutsideAllowedRoots() throws Exception {
        Path projects = tempDir.resolve("projects");
        Path outside = tempDir.resolve("outside").resolve("nexus");
        Files.createDirectories(projects);
        Files.createDirectories(outside);
        Files.writeString(outside.resolve(".env"), "SECRET=x\n");

        ContainerInventory inventory = inventory(snapshot(
                "pg1",
                "nexus-postgres-1",
                Map.of(
                        "com.docker.compose.project", "nexus",
                        "com.docker.compose.project.working_dir", outside.toString())));

        FileProjectDotEnvStore store = new FileProjectDotEnvStore(
                new FakeManifestCatalog(), inventory, projects, List.of(), null);

        assertThat(store.read("nexus")).isEmpty();
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

    private static ContainerInventory emptyInventory() {
        return inventory();
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
                return all.stream().filter(s -> s.id().equals(containerId)).findFirst();
            }

            @Override
            public Optional<ContainerInspect> inspect(String containerId) {
                return Optional.empty();
            }
        };
    }

    private static ContainerSnapshot snapshot(String id, String name, Map<String, String> labels) {
        return new ContainerSnapshot(
                id,
                name,
                "postgres:16",
                "Up",
                "running",
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                labels,
                List.of(),
                0,
                Instant.parse("2026-01-01T00:00:01Z"));
    }
}
