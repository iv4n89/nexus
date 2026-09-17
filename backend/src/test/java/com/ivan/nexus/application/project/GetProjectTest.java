package com.ivan.nexus.application.project;

import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProjectTest {

    @Test
    void returnsOverviewWhenManifestExistsWithoutContainers(@TempDir Path allowedRoot) {
        ProjectManifest manifest = new ProjectManifest(
                new ProjectManifest.ProjectBlock("solo", "solo", null, allowedRoot.resolve("solo").toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
        FakeManifestCatalog manifests = new FakeManifestCatalog()
                .add("solo", manifest, allowedRoot.resolve("solo/nexus.yml"));

        GetProject getProject = new GetProject(new DiscoverProjects(emptyInventory(), manifests));
        GetProject.Result result = getProject.execute("solo");

        assertThat(result.project().id()).isEqualTo("solo");
        assertThat(result.project().status()).isEqualTo("DOWN");
        assertThat(result.project().totalCount()).isEqualTo(0);
        assertThat(result.project().deployable()).isTrue();
        assertThat(result.containers()).isEmpty();
    }

    @Test
    void unknownProjectIsNotFound(@TempDir Path allowedRoot) {
        GetProject getProject = new GetProject(new DiscoverProjects(emptyInventory(), new FakeManifestCatalog()));

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
