package com.ivan.nexus.domain.manifest;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestValidatorTest {
    @TempDir
    Path allowedRoot;

    @Test
    void validManifestLoadsAndValidates() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", workingDirectory().toString()),
                List.of("api", "web"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                new ProjectManifest.CommandBlock("./rollback.sh"),
                new ProjectManifest.HealthBlock("http://127.0.0.1:18080", 5),
                new ProjectManifest.AlertsBlock(10, 3, 90));

        ManifestValidator.validate(manifest, allowedRoot);

        assertThat(manifest.project().id()).isEqualTo("lab");
        assertThat(manifest.project().workingDirectory()).isEqualTo(workingDirectory().toString());
        assertThat(manifest.services()).containsExactly("api", "web");
        assertThat(manifest.deployment().command()).isEqualTo("./deploy.sh");
        assertThat(manifest.rollback().command()).isEqualTo("./rollback.sh");
        assertThat(manifest.health().url()).isEqualTo("http://127.0.0.1:18080");
        assertThat(manifest.alerts().memoryPercent()).isEqualTo(90);
    }

    @Test
    void missingCommandIsInvalid() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", workingDirectory().toString()),
                List.of("api"),
                null,
                null,
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, allowedRoot));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "./deploy.sh && rm -rf /",
            "./deploy.sh | cat",
            "./$(whoami)",
            "./deploy.sh `id`"
    })
    void rejectsDestructiveCommands(String command) {
        assertInvalid(() -> ManifestValidator.validate(validManifest(command), allowedRoot));
    }

    @Test
    void rejectsRollbackCommandWithShellMetacharacters() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", workingDirectory().toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                new ProjectManifest.CommandBlock("./rollback.sh && rm -rf /"),
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, allowedRoot));
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

        assertInvalid(() -> ManifestValidator.validate(manifest, allowedRoot));
    }

    @Test
    void rejectsWorkingDirectoryEscapingAllowedRoot() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", allowedRoot.resolve("lab/../../elsewhere").toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, allowedRoot));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingProjectId(String id) {
        ProjectManifest manifest = new ProjectManifest(
                project(id, workingDirectory().toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, allowedRoot));
    }

    @Test
    void rejectsMetadataHealthUrl() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", workingDirectory().toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                new ProjectManifest.HealthBlock("http://169.254.169.254/", 5),
                null);

        assertInvalid(() -> ManifestValidator.validate(manifest, allowedRoot));
    }

    @Test
    void allowsMissingHealthRollbackAndAlerts() {
        ProjectManifest manifest = new ProjectManifest(
                project("lab", workingDirectory().toString()),
                List.of("api"),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);

        ManifestValidator.validate(manifest, allowedRoot);
    }

    private Path workingDirectory() {
        return allowedRoot.resolve("lab");
    }

    private ProjectManifest validManifest(String command) {
        return new ProjectManifest(
                project("lab", workingDirectory().toString()),
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
