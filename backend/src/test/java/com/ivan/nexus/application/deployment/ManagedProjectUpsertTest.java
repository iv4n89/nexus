package com.ivan.nexus.application.deployment;

import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectEntity;
import com.ivan.nexus.infrastructure.persistence.project.ManagedProjectJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagedProjectUpsertTest {

    @TempDir
    Path tempDir;

    @Test
    void insertsThenUpdatesFromManifest() {
        ConcurrentMap<String, ManagedProjectEntity> store = new ConcurrentHashMap<>();
        ManagedProjectJpaRepository projects = mock(ManagedProjectJpaRepository.class);
        when(projects.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(projects.save(any())).thenAnswer(invocation -> {
            ManagedProjectEntity entity = invocation.getArgument(0);
            store.put(entity.getId(), entity);
            return entity;
        });
        ManagedProjectUpsert upsert = new ManagedProjectUpsert(projects);
        Path workingDirectory = tempDir.resolve("lab");
        Path manifestPath = workingDirectory.resolve("nexus.yml");

        upsert.upsertProject(manifest("Lab", "first"), workingDirectory, manifestPath);
        ManagedProjectEntity created = store.get("lab");
        assertThat(created.getName()).isEqualTo("Lab");
        assertThat(created.getDescription()).isEqualTo("first");
        assertThat(created.getWorkingDirectory()).isEqualTo(workingDirectory.toString());
        assertThat(created.getManifestPath()).isEqualTo(manifestPath.toAbsolutePath().normalize().toString());

        upsert.upsertProject(manifest("Lab 2", "second"), workingDirectory, manifestPath);
        assertThat(store).hasSize(1);
        assertThat(store.get("lab").getName()).isEqualTo("Lab 2");
        assertThat(store.get("lab").getDescription()).isEqualTo("second");
    }

    @Test
    void blankNameFallsBackToId() {
        assertThat(ManagedProjectUpsert.blankToId(null, "lab")).isEqualTo("lab");
        assertThat(ManagedProjectUpsert.blankToId("  ", "lab")).isEqualTo("lab");
        assertThat(ManagedProjectUpsert.blankToId("Lab", "lab")).isEqualTo("Lab");
    }

    private static ProjectManifest manifest(String name, String description) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock("lab", name, description, "/tmp/lab"),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
    }
}
