package com.ivan.nexus.infrastructure.env;

import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.LoadedManifest;
import com.ivan.nexus.application.manifest.ManifestCatalog;
import com.ivan.nexus.domain.env.DotEnvParser;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class FileProjectDotEnvStore implements ProjectDotEnvStore {
    private final ManifestCatalog manifests;
    private final Path allowedRoot;

    public FileProjectDotEnvStore(
            ManifestCatalog manifests,
            @Qualifier("manifestAllowedRoot") Path allowedRoot) {
        this.manifests = manifests;
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    @Override
    public Map<String, String> read(String projectId) {
        Path envFile = resolveEnvPath(projectId);
        if (!Files.isRegularFile(envFile)) {
            return new LinkedHashMap<>();
        }
        try {
            String content = Files.readString(envFile, StandardCharsets.UTF_8);
            return new LinkedHashMap<>(DotEnvParser.parse(content));
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Unable to read .env");
        }
    }

    @Override
    public void write(String projectId, Map<String, String> values) {
        Path envFile = resolveEnvPath(projectId);
        try {
            Files.createDirectories(envFile.getParent());
            Path temp = envFile.resolveSibling(".env.nexus.tmp");
            Files.writeString(temp, DotEnvParser.format(values), StandardCharsets.UTF_8);
            trySetOwnerOnly(temp);
            Files.move(temp, envFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            trySetOwnerOnly(envFile);
        } catch (IOException ex) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Unable to write .env");
        }
    }

    Path resolveEnvPath(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            throw new DomainException(NexusErrorCode.PROJECT_NOT_FOUND, "Project id is required");
        }
        Path projectDir;
        try {
            LoadedManifest loaded = manifests.loadRequired(projectId);
            String working = loaded.manifest().project().workingDirectory();
            projectDir = Path.of(working).toAbsolutePath().normalize();
        } catch (DomainException ex) {
            projectDir = allowedRoot.resolve(projectId).toAbsolutePath().normalize();
        }
        if (!projectDir.startsWith(allowedRoot)) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Invalid project path");
        }
        Path envFile = projectDir.resolve(".env").toAbsolutePath().normalize();
        if (!envFile.startsWith(allowedRoot)) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Invalid .env path");
        }
        return envFile;
    }

    private static void trySetOwnerOnly(Path path) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // non-POSIX FS
        }
    }
}
