package com.ivan.nexus.domain.manifest;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.manifest.YamlManifestLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestValidatorTest {
    private static final Path ALLOWED_ROOT = Path.of("/tmp/nexus-manifest-fixtures");
    private static final Path WORKING_DIR = ALLOWED_ROOT.resolve("lab");

    private final YamlManifestLoader loader = new YamlManifestLoader();

    @BeforeAll
    static void createWorkingDir() throws IOException {
        Files.createDirectories(WORKING_DIR);
    }

    @Test
    void validManifestLoadsAndValidates() {
        ProjectManifest manifest = load("/manifests/valid.yml");

        ManifestValidator.validate(manifest, ALLOWED_ROOT);

        assertThat(manifest.project().id()).isEqualTo("lab");
        assertThat(manifest.project().workingDirectory()).isEqualTo(WORKING_DIR.toString());
        assertThat(manifest.services()).containsExactly("api", "web");
        assertThat(manifest.deployment().command()).isEqualTo("./deploy.sh");
        assertThat(manifest.rollback().command()).isEqualTo("./rollback.sh");
        assertThat(manifest.health().url()).isEqualTo("http://127.0.0.1:18080");
        assertThat(manifest.alerts().memoryPercent()).isEqualTo(90);
    }

    @Test
    void missingCommandIsInvalid() {
        ProjectManifest manifest = load("/manifests/missing-command.yml");

        assertInvalid(() -> ManifestValidator.validate(manifest, ALLOWED_ROOT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "./deploy.sh && rm -rf /",
            "./deploy.sh | cat",
            "./$(whoami)",
            "./deploy.sh `id`"
    })
    void rejectsDestructiveCommands(String command) {
        assertInvalid(() -> ManifestValidator.validate(validManifest(command), ALLOWED_ROOT));
    }

    @Test
    void rejectsRollbackCommandWithShellMetacharacters() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", WORKING_DIR.toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                new ProjectManifest.CommandBlock("./rollback.sh && rm -rf /"),
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, ALLOWED_ROOT));
    }

    @Test
    void rejectsWorkingDirectoryOutsideAllowedRoot() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", "/tmp/elsewhere/lab"),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, ALLOWED_ROOT));
    }

    @Test
    void rejectsWorkingDirectoryEscapingAllowedRoot() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", ALLOWED_ROOT.resolve("lab/../../elsewhere").toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, ALLOWED_ROOT));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingProjectId(String id) {
        ProjectManifest manifest = new ProjectManifest(
                project(id, WORKING_DIR.toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, ALLOWED_ROOT));
    }

    @Test
    void rejectsMetadataHealthUrl() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", WORKING_DIR.toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                new ProjectManifest.HealthBlock("http://169.254.169.254/", 5),
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, ALLOWED_ROOT));
    }

    @Test
    void allowsMissingHealthRollbackAndAlerts() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", WORKING_DIR.toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);

        ManifestValidator.validate(manifest, ALLOWED_ROOT);
    }

    private ProjectManifest load(String classpath) {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("fixture %s", classpath).isNotNull();
            return loader.load(in);
        } catch (IOException ex) {
            throw new AssertionError("failed to load " + classpath, ex);
        }
    }

    private static ProjectManifest validManifest(String command) {
        return new ProjectManifest(
                project("lab", WORKING_DIR.toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock(command),
                null,
                null,
                null);
    }

    private static ProjectManifest.ProjectBlock project(String id, String workingDirectory) {
        return new ProjectManifest.ProjectBlock(id, "Lab", "Test fixture", workingDirectory);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getCode())
                        .isEqualTo(NexusErrorCode.MANIFEST_INVALID));
    }
}
