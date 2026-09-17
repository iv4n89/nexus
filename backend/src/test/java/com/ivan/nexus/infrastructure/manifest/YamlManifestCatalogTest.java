package com.ivan.nexus.infrastructure.manifest;

import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "../outside", "nested/project", "nested\\project"})
    void unsafeProjectIdsAreRejectedAsNotFound(String projectId) {
        assertThat(catalog().exists(projectId)).isFalse();
        assertFailure(
                () -> catalog().loadRequired(projectId),
                NexusErrorCode.MANIFEST_NOT_FOUND,
                "Manifest not found");
    }

    @Test
    void absoluteProjectIdCannotReadManifestOutsideRoot() throws IOException {
        Path allowedRoot = root.resolve("allowed");
        Path outsideProject = root.resolve("outside");
        Files.createDirectories(allowedRoot);
        writeManifestAt(outsideProject, outsideProject.toAbsolutePath().toString(), outsideProject);
        YamlManifestCatalog catalog = new YamlManifestCatalog(allowedRoot);
        String absoluteId = outsideProject.toAbsolutePath().toString();

        assertThat(catalog.exists(absoluteId)).isFalse();
        assertFailure(
                () -> catalog.loadRequired(absoluteId),
                NexusErrorCode.MANIFEST_NOT_FOUND,
                "Manifest not found");
    }

    @Test
    void parentTraversalCannotReadManifestOutsideRoot() throws IOException {
        Path allowedRoot = root.resolve("allowed");
        Path outsideProject = root.resolve("outside");
        Files.createDirectories(allowedRoot);
        writeManifestAt(outsideProject, "../outside", outsideProject);
        YamlManifestCatalog catalog = new YamlManifestCatalog(allowedRoot);

        assertThat(catalog.exists("../outside")).isFalse();
        assertFailure(
                () -> catalog.loadRequired("../outside"),
                NexusErrorCode.MANIFEST_NOT_FOUND,
                "Manifest not found");
    }

    @Test
    void symlinkedProjectEscapingRootIsUnusableAndUndiscoverable() throws IOException {
        Path allowedRoot = root.resolve("allowed");
        Path outsideProject = root.resolve("outside-project");
        Files.createDirectories(allowedRoot);
        writeManifestAt(outsideProject, "linked", outsideProject);
        createSymlinkOrSkip(allowedRoot.resolve("linked"), outsideProject);
        YamlManifestCatalog catalog = new YamlManifestCatalog(allowedRoot);

        assertThat(catalog.discoverProjectIds()).doesNotContain("linked");
        assertThat(catalog.exists("linked")).isFalse();
        assertFailure(
                () -> catalog.loadRequired("linked"),
                NexusErrorCode.MANIFEST_NOT_FOUND,
                "Manifest not found");
    }

    @Test
    void symlinkedManifestEscapingRootIsUnusableAndUndiscoverable() throws IOException {
        Path allowedRoot = root.resolve("allowed");
        Path project = allowedRoot.resolve("linked-file");
        Path outsideProject = root.resolve("outside-manifest");
        Files.createDirectories(project);
        writeManifestAt(outsideProject, "linked-file", project);
        createSymlinkOrSkip(project.resolve("nexus.yml"), outsideProject.resolve("nexus.yml"));
        YamlManifestCatalog catalog = new YamlManifestCatalog(allowedRoot);

        assertThat(catalog.discoverProjectIds()).doesNotContain("linked-file");
        assertThat(catalog.exists("linked-file")).isFalse();
        assertFailure(
                () -> catalog.loadRequired("linked-file"),
                NexusErrorCode.MANIFEST_NOT_FOUND,
                "Manifest not found");
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
        writeManifestAt(project, embeddedId, workingDirectory);
    }

    private static void writeManifestAt(Path project, String embeddedId, Path workingDirectory) throws IOException {
        Files.createDirectories(project);
        Files.writeString(project.resolve("nexus.yml"), """
                project:
                  id: %s
                  workingDirectory: %s
                deployment:
                  command: ./deploy.sh
                """.formatted(embeddedId, workingDirectory.toAbsolutePath()));
    }

    private static void createSymlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target.toAbsolutePath());
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable: " + exception.getMessage());
        }
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
