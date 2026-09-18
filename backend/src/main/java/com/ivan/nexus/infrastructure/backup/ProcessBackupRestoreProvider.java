package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupException;
import com.ivan.nexus.application.backup.BackupRestoreProvider;

import java.util.List;

/**
 * Restores Postgres custom-format dumps via {@code pg_restore} and volume tarballs via Docker.
 * Skeleton: operations are invoked through ProcessBuilder when backup restore is enabled.
 */
public class ProcessBackupRestoreProvider implements BackupRestoreProvider {
    private final String pgRestoreExecutable;
    private final CommandRunner runner;

    public ProcessBackupRestoreProvider(String pgRestoreExecutable) {
        this(pgRestoreExecutable, ProcessBackupRestoreProvider::run);
    }

    ProcessBackupRestoreProvider(String pgRestoreExecutable, CommandRunner runner) {
        this.pgRestoreExecutable = pgRestoreExecutable == null || pgRestoreExecutable.isBlank()
                ? "pg_restore"
                : pgRestoreExecutable;
        this.runner = runner;
    }

    @Override
    public void restorePostgres(String projectId, String artifactUri) {
        if (artifactUri == null || artifactUri.isBlank()) {
            throw new BackupException("Missing postgres artifact URI");
        }
        int code = runner.run(List.of(pgRestoreExecutable, "--clean", "--if-exists", pathFromUri(artifactUri)));
        if (code != 0) {
            throw new BackupException("pg_restore exited with code " + code);
        }
    }

    @Override
    public void restoreVolumes(String projectId, String artifactUri, List<String> volumeNames) {
        if (artifactUri == null || artifactUri.isBlank()) {
            throw new BackupException("Missing volume artifact URI");
        }
        if (volumeNames == null || volumeNames.isEmpty()) {
            return;
        }
        String path = pathFromUri(artifactUri);
        for (String volume : volumeNames) {
            int code = runner.run(List.of(
                    "docker", "run", "--rm",
                    "-v", volume + ":/data",
                    "-v", parentDir(path) + ":/backup:ro",
                    "alpine:3.20",
                    "sh", "-c", "cd /data && tar xzf /backup/" + fileName(path)));
            if (code != 0) {
                throw new BackupException("volume restore failed for " + volume + " (exit " + code + ")");
            }
        }
    }

    private static String pathFromUri(String uri) {
        if (uri.startsWith("file:")) {
            return java.nio.file.Path.of(java.net.URI.create(uri)).toString();
        }
        return uri;
    }

    private static String parentDir(String path) {
        return java.nio.file.Path.of(path).getParent().toAbsolutePath().toString();
    }

    private static String fileName(String path) {
        return java.nio.file.Path.of(path).getFileName().toString();
    }

    private static int run(List<String> argv) {
        try {
            Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        } catch (Exception ex) {
            throw new BackupException("Failed to run restore command", ex);
        }
    }

    @FunctionalInterface
    interface CommandRunner {
        int run(List<String> argv);
    }
}
