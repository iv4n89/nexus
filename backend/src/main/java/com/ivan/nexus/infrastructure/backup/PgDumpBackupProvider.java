package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupException;
import com.ivan.nexus.application.backup.BackupProvider;
import com.ivan.nexus.application.backup.PostgresDumpResult;
import com.ivan.nexus.application.database.DiscoverProjectDatabases;
import com.ivan.nexus.application.database.InstanceResolution;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.ResolvedTarget;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Runs {@code pg_dump} via ProcessBuilder and stores the dump under the configured local path.
 */
public class PgDumpBackupProvider implements BackupProvider {
    private static final Logger log = LoggerFactory.getLogger(PgDumpBackupProvider.class);
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final DiscoverProjectDatabases databases;
    private final NexusProperties.Backup settings;
    private final Function<DumpCommand, ProcessResult> processRunner;

    public PgDumpBackupProvider(DiscoverProjectDatabases databases, NexusProperties.Backup settings) {
        this(databases, settings, PgDumpBackupProvider::runProcess);
    }

    PgDumpBackupProvider(
            DiscoverProjectDatabases databases,
            NexusProperties.Backup settings,
            Function<DumpCommand, ProcessResult> processRunner) {
        this.databases = databases;
        this.settings = settings;
        this.processRunner = processRunner;
    }

    @Override
    public PostgresDumpResult dumpPostgres(String projectId) {
        InstanceResolution resolution = selectPostgres(projectId);
        ResolvedTarget target = resolution.target();
        Path artifact = artifactPath(projectId, resolution.instance().defaultDatabase());
        try {
            Files.createDirectories(artifact.getParent());
        } catch (IOException ex) {
            throw new BackupException("Failed to create backup directory: " + artifact.getParent(), ex);
        }

        List<String> argv = buildArgv(target, artifact);
        log.info("Running pg_dump for project {} -> {}", projectId, artifact);
        ProcessResult result = processRunner.apply(new DumpCommand(argv, target.password()));
        if (result.exitCode() != 0) {
            safeDelete(artifact);
            throw new BackupException(
                    "pg_dump exited with code " + result.exitCode() + ": " + truncate(result.stderr()));
        }
        long size;
        try {
            size = Files.size(artifact);
        } catch (IOException ex) {
            throw new BackupException("pg_dump succeeded but artifact is missing: " + artifact, ex);
        }
        String uri = artifact.toAbsolutePath().normalize().toUri().toString();
        String dbName = target.defaultDatabase() == null ? "postgres" : target.defaultDatabase();
        return new PostgresDumpResult(
                uri,
                size,
                dbName,
                "postgres dump of " + dbName + " (" + size + " bytes)");
    }

    List<String> buildArgv(ResolvedTarget target, Path artifact) {
        List<String> argv = new ArrayList<>();
        argv.add(blankToDefault(settings.getPgDumpExecutable(), "pg_dump"));
        argv.add("--host=" + target.host());
        argv.add("--port=" + target.port());
        argv.add("--username=" + target.username());
        argv.add("--dbname=" + (target.defaultDatabase() == null ? "postgres" : target.defaultDatabase()));
        argv.add("--file=" + artifact);
        argv.add("--no-password");
        argv.add("--format=custom");
        return List.copyOf(argv);
    }

    private InstanceResolution selectPostgres(String projectId) {
        return databases.execute(projectId).stream()
                .filter(instance -> instance.engine() == DatabaseEngine.POSTGRES)
                .filter(instance -> instance.status() == DatabaseStatus.READY)
                .map(instance -> databases.resolve(projectId, instance.id()))
                .filter(resolution -> resolution.target() != null)
                .findFirst()
                .orElseThrow(() -> new BackupException(
                        "No reachable Postgres instance found for project " + projectId));
    }

    private Path artifactPath(String projectId, String databaseName) {
        String safeProject = sanitize(projectId);
        String safeDb = sanitize(databaseName == null || databaseName.isBlank() ? "postgres" : databaseName);
        String fileName = safeProject + "-" + safeDb + "-" + FILE_TS.format(Instant.now()) + ".dump";
        return Path.of(settings.getLocalPath()).toAbsolutePath().normalize()
                .resolve(safeProject)
                .resolve(fileName);
    }

    private static ProcessResult runProcess(DumpCommand command) {
        ProcessBuilder builder = new ProcessBuilder(command.argv());
        Map<String, String> env = builder.environment();
        if (command.password() != null) {
            env.put("PGPASSWORD", command.password());
        }
        builder.redirectErrorStream(false);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new BackupException("Failed to start pg_dump", ex);
        }
        try {
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new BackupException("pg_dump timed out");
            }
            return new ProcessResult(process.exitValue(), stdout, stderr);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BackupException("pg_dump interrupted", ex);
        } catch (IOException ex) {
            process.destroyForcibly();
            throw new BackupException("Failed to read pg_dump output", ex);
        }
    }

    private static void safeDelete(Path artifact) {
        try {
            Files.deleteIfExists(artifact);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    record DumpCommand(List<String> argv, String password) {}

    record ProcessResult(int exitCode, String stdout, String stderr) {}
}
