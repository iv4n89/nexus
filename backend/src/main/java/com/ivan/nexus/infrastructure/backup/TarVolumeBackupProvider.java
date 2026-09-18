package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupException;
import com.ivan.nexus.application.backup.VolumeBackupProvider;
import com.ivan.nexus.application.backup.VolumeBackupResult;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Exports declared Docker volumes via {@code docker run} + {@code tar}.
 */
public class TarVolumeBackupProvider implements VolumeBackupProvider {
    private static final Logger log = LoggerFactory.getLogger(TarVolumeBackupProvider.class);
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final NexusProperties.Backup settings;
    private final Function<List<String>, ProcessResult> processRunner;

    public TarVolumeBackupProvider(NexusProperties.Backup settings) {
        this(settings, TarVolumeBackupProvider::runProcess);
    }

    TarVolumeBackupProvider(NexusProperties.Backup settings, Function<List<String>, ProcessResult> processRunner) {
        this.settings = settings;
        this.processRunner = processRunner;
    }

    @Override
    public VolumeBackupResult backupVolumes(String projectId, List<String> volumeNames) {
        if (volumeNames == null || volumeNames.isEmpty()) {
            return new VolumeBackupResult(null, 0L, List.of(), "no volumes declared");
        }
        Path artifact = artifactPath(projectId);
        try {
            Files.createDirectories(artifact.getParent());
        } catch (IOException ex) {
            throw new BackupException("Failed to create volume backup directory: " + artifact.getParent(), ex);
        }

        List<String> argv = buildArgv(volumeNames, artifact);
        log.info("Backing up volumes {} for project {} -> {}", volumeNames, projectId, artifact);
        ProcessResult result = processRunner.apply(argv);
        if (result.exitCode() != 0) {
            safeDelete(artifact);
            throw new BackupException(
                    "volume backup exited with code " + result.exitCode() + ": " + truncate(result.stderr()));
        }
        long size;
        try {
            size = Files.size(artifact);
        } catch (IOException ex) {
            throw new BackupException("volume backup succeeded but artifact is missing: " + artifact, ex);
        }
        String uri = artifact.toAbsolutePath().normalize().toUri().toString();
        return new VolumeBackupResult(
                uri,
                size,
                List.copyOf(volumeNames),
                "volumes " + String.join(",", volumeNames) + " (" + size + " bytes)");
    }

    List<String> buildArgv(List<String> volumeNames, Path artifact) {
        List<String> argv = new ArrayList<>();
        argv.add("docker");
        argv.add("run");
        argv.add("--rm");
        for (String volume : volumeNames) {
            argv.add("-v");
            argv.add(volume + ":/backup/" + volume + ":ro");
        }
        argv.add("-v");
        argv.add(artifact.getParent().toAbsolutePath() + ":/out");
        argv.add("alpine:3.20");
        argv.add("tar");
        argv.add("czf");
        argv.add("/out/" + artifact.getFileName());
        argv.add("-C");
        argv.add("/backup");
        argv.add(".");
        return List.copyOf(argv);
    }

    private Path artifactPath(String projectId) {
        String safeProject = sanitize(projectId);
        String fileName = safeProject + "-volumes-" + FILE_TS.format(Instant.now()) + ".tar.gz";
        return Path.of(settings.getLocalPath()).toAbsolutePath().normalize()
                .resolve(safeProject)
                .resolve(fileName);
    }

    private static ProcessResult runProcess(List<String> argv) {
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new BackupException("Failed to start volume backup", ex);
        }
        try {
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new BackupException("volume backup timed out");
            }
            return new ProcessResult(process.exitValue(), stdout, stderr);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BackupException("volume backup interrupted", ex);
        } catch (IOException ex) {
            process.destroyForcibly();
            throw new BackupException("Failed to read volume backup output", ex);
        }
    }

    private static void safeDelete(Path artifact) {
        try {
            Files.deleteIfExists(artifact);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    record ProcessResult(int exitCode, String stdout, String stderr) {}
}
