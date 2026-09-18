package com.ivan.nexus.infrastructure.persistence.project;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

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
    void atomicallyUpsertsAllFieldsWithBlankNameFallback() {
        Path workingDirectory = Path.of("/srv/lab");
        Path manifestPath = workingDirectory.resolve("nexus.yml");

        store.upsert(manifest("  ", "first"), workingDirectory, manifestPath);

        verify(repository).upsertProject(
                "lab",
                "lab",
                "first",
                "/srv/lab",
                "/srv/lab/nexus.yml");
    }

    @Test
    void delegatesDeepUpdateValuesToAtomicQuery() {
        store.upsert(
                manifest("Lab 2", "second"),
                Path.of("/srv/lab"),
                Path.of("/srv/lab/custom.yml"));

        verify(repository).upsertProject(
                "lab",
                "Lab 2",
                "second",
                "/srv/lab",
                "/srv/lab/custom.yml");
    }

    @Test
    void linksGitHubCoordinates() {
        when(repository.linkGitHub("lab", "octo", "lab-repo", "main")).thenReturn(1);

        store.linkGitHub("lab", "octo", "lab-repo", "main");

        verify(repository).linkGitHub("lab", "octo", "lab-repo", "main");
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
