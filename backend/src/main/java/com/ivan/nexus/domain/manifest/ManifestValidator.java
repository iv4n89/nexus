package com.ivan.nexus.domain.manifest;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class ManifestValidator {
    private static final Pattern COMMAND_PATTERN =
            Pattern.compile("^[./a-zA-Z0-9._-]+(?:\\s+[./a-zA-Z0-9._-]+)*$");

    private final Path allowedRoot;

    public ManifestValidator(Path allowedRoot) {
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    @Autowired
    public ManifestValidator(NexusProperties properties) {
        this(Path.of(properties.getManifest().getAllowedRoot()));
    }

    public void validate(ProjectManifest manifest) {
        if (manifest == null || manifest.project() == null) {
            fail("project is required");
        }
        if (isBlank(manifest.project().id())) {
            fail("project.id is required");
        }
        if (isBlank(manifest.project().workingDirectory())) {
            fail("project.workingDirectory is required");
        }

        Path workingDirectory = Path.of(manifest.project().workingDirectory()).toAbsolutePath().normalize();
        if (!workingDirectory.startsWith(allowedRoot)) {
            fail("project.workingDirectory must be under the allowed root");
        }

        if (manifest.deployment() == null || isBlank(manifest.deployment().command())) {
            fail("deployment.command is required");
        }
        validateCommand(manifest.deployment().command(), "deployment.command");

        if (manifest.rollback() != null && !isBlank(manifest.rollback().command())) {
            validateCommand(manifest.rollback().command(), "rollback.command");
        }
    }

    private static void validateCommand(String command, String field) {
        if (!COMMAND_PATTERN.matcher(command).matches()) {
            fail(field + " must be a relative path without shell metacharacters");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void fail(String message) {
        throw new DomainException(NexusErrorCode.MANIFEST_INVALID, message);
    }
}
