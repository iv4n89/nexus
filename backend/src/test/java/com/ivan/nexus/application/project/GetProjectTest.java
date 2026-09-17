package com.ivan.nexus.application.project;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProjectTest {

    @Test
    void returnsOverviewWhenManifestExistsWithoutContainers(@TempDir Path allowedRoot) throws IOException {
        Path manifest = allowedRoot.resolve("solo").resolve("nexus.yml");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "project:\n  id: solo\n");

        GetProject getProject = new GetProject(new DiscoverProjects(emptyInventory(), allowedRoot.toString()));
        GetProject.Result result = getProject.execute("solo");

        assertThat(result.project().id()).isEqualTo("solo");
        assertThat(result.project().status()).isEqualTo("DOWN");
        assertThat(result.project().totalCount()).isEqualTo(0);
        assertThat(result.project().deployable()).isTrue();
        assertThat(result.containers()).isEmpty();
    }

    @Test
    void unknownProjectIsNotFound(@TempDir Path allowedRoot) {
        GetProject getProject = new GetProject(new DiscoverProjects(emptyInventory(), allowedRoot.toString()));

        assertThatThrownBy(() -> getProject.execute("missing"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(NexusErrorCode.PROJECT_NOT_FOUND);
    }

    private static ContainerInventory emptyInventory() {
        return new ContainerInventory() {
            @Override
            public java.util.List<com.ivan.nexus.domain.container.ContainerSnapshot> listAll() {
                return List.of();
            }

            @Override
            public Optional<com.ivan.nexus.domain.container.ContainerSnapshot> findById(String containerId) {
                return Optional.empty();
            }
        };
    }
}
