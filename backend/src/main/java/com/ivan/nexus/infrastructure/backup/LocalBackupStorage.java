package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores backup artifacts on the local filesystem under the configured root.
 */
public class LocalBackupStorage implements BackupStorage {
    private final Path root;

    public LocalBackupStorage(String localPath) {
        this.root = Path.of(localPath).toAbsolutePath().normalize();
    }

    @Override
    public String store(String projectId, String localPath) {
        if (localPath == null || localPath.isBlank()) {
            throw new IllegalArgumentException("localPath is required");
        }
        Path source = Path.of(localPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new UncheckedIOException(new IOException("Backup artifact missing: " + source));
        }
        try {
            Files.createDirectories(root.resolve(sanitize(projectId)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return source.toUri().toString();
    }

    @Override
    public boolean exists(String artifactUri) {
        if (artifactUri == null || artifactUri.isBlank()) {
            return false;
        }
        try {
            Path path = Path.of(URI.create(artifactUri));
            return Files.isRegularFile(path);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public void delete(String artifactUri) {
        if (artifactUri == null || artifactUri.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(URI.create(artifactUri)));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
