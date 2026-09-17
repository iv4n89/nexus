package com.ivan.nexus.infrastructure.persistence.project;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaManagedProjectStoreTest {
    private ManagedProjectJpaRepository repository;
    private ManagedProjectStore store;

    @BeforeEach
    void setUp() {
        repository = mock(ManagedProjectJpaRepository.class);
        store = new JpaManagedProjectStore(repository);
    }

    @Test
    void createsProjectWithBlankNameFallbackAndNormalizedPaths() {
        Path workingDirectory = Path.of("/srv/lab");
        Path manifestPath = workingDirectory.resolve("nexus.yml");
        when(repository.findById("lab")).thenReturn(Optional.empty());

        store.upsert(manifest("  ", "first"), workingDirectory, manifestPath);

        ArgumentCaptor<ManagedProjectEntity> captor = ArgumentCaptor.forClass(ManagedProjectEntity.class);
        verify(repository).save(captor.capture());
        ManagedProjectEntity entity = captor.getValue();
        assertThat(entity.getName()).isEqualTo("lab");
        assertThat(entity.getDescription()).isEqualTo("first");
        assertThat(entity.getWorkingDirectory()).isEqualTo("/srv/lab");
        assertThat(entity.getManifestPath()).isEqualTo("/srv/lab/nexus.yml");
    }

    @Test
    void loadsAndDeeplyUpdatesExistingProject() {
        ManagedProjectEntity entity =
                new ManagedProjectEntity("lab", "Old", "old", "/old", "/old/nexus.yml");
        when(repository.findById("lab")).thenReturn(Optional.of(entity));

        store.upsert(
                manifest("Lab 2", "second"),
                Path.of("/srv/lab"),
                Path.of("/srv/lab/custom.yml"));

        verify(repository).save(entity);
        assertThat(entity.getName()).isEqualTo("Lab 2");
        assertThat(entity.getDescription()).isEqualTo("second");
        assertThat(entity.getWorkingDirectory()).isEqualTo("/srv/lab");
        assertThat(entity.getManifestPath()).isEqualTo("/srv/lab/custom.yml");
    }

    private static ProjectManifest manifest(String name, String description) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock("lab", name, description, "/ignored"),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
    }
}
