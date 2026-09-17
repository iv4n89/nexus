package com.ivan.nexus.infrastructure.manifest;

import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlManifestCatalogTest {

    @TempDir
    Path root;

    @Test
    void discoversOnlyDirectoriesContainingRegularManifestFiles() throws IOException {
        writeManifest("valid", "valid", root.resolve("valid"));
        Files.createDirectories(root.resolve("missing"));
        Files.createDirectories(root.resolve("directory").resolve("nexus.yml"));
        Files.writeString(root.resolve("plain-file"), "ignored");

        assertThat(catalog().discoverProjectIds()).containsExactly("valid");
    }

    @Test
    void missingRootProducesEmptyDiscoveryAndLeavesExistenceFalse() throws IOException {
        Path missingRoot = root.resolve("missing-root");
        YamlManifestCatalog catalog = new YamlManifestCatalog(missingRoot);

        assertThat(catalog.discoverProjectIds()).isEmpty();
        assertThat(catalog.exists("lab")).isFalse();
    }

    @Test
    void deployabilityRequiresARegularManifestFile() throws IOException {
        Files.createDirectories(root.resolve("directory").resolve("nexus.yml"));
        writeManifest("valid", "valid", root.resolve("valid"));

        assertThat(catalog().exists("directory")).isFalse();
        assertThat(catalog().exists("valid")).isTrue();
    }

    @Test
    void missingRequiredManifestKeepsNotFoundContract() {
        assertFailure(
                () -> catalog().loadRequired("missing"),
                NexusErrorCode.MANIFEST_NOT_FOUND,
                "Manifest not found");
    }

    @Test
    void malformedYamlKeepsInvalidContract() throws IOException {
        Path project = root.resolve("lab");
        Files.createDirectories(project);
        Files.writeString(project.resolve("nexus.yml"), "project: [");

        assertFailure(
                () -> catalog().loadRequired("lab"),
                NexusErrorCode.MANIFEST_INVALID,
                "Unable to parse manifest YAML");
    }

    @Test
    void embeddedProjectIdMustMatchRequestedId() throws IOException {
        writeManifest("lab", "other", root.resolve("lab"));

        assertFailure(
                () -> catalog().loadRequired("lab"),
                NexusErrorCode.MANIFEST_INVALID,
                "project.id does not match");
    }

    @Test
    void appliesManifestValidationIncludingAllowedRoot() throws IOException {
        writeManifest("lab", "lab", root.resolveSibling("outside"));

        assertFailure(
                () -> catalog().loadRequired("lab"),
                NexusErrorCode.MANIFEST_INVALID,
                "project.workingDirectory must be under the allowed root");
    }

    @Test
    void returnsValidatedManifestAndNormalizedPath() throws IOException {
        Path project = root.resolve("lab");
        writeManifest("lab", "lab", project);

        LoadedManifest loaded = catalog().loadRequired("lab");

        assertThat(loaded.manifest().project().id()).isEqualTo("lab");
        assertThat(loaded.manifestPath())
                .isEqualTo(project.resolve("nexus.yml").toAbsolutePath().normalize());
    }

    private YamlManifestCatalog catalog() {
        return new YamlManifestCatalog(root);
    }

    private void writeManifest(String directory, String embeddedId, Path workingDirectory) throws IOException {
        Path project = root.resolve(directory);
        Files.createDirectories(project);
        Files.writeString(project.resolve("nexus.yml"), """
                project:
                  id: %s
                  workingDirectory: %s
                deployment:
                  command: ./deploy.sh
                """.formatted(embeddedId, workingDirectory.toAbsolutePath()));
    }

    private static void assertFailure(
            Runnable operation,
            NexusErrorCode code,
            String message) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    DomainException exception = (DomainException) error;
                    assertThat(exception.getCode()).isEqualTo(code);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });
    }
}
