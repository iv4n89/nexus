package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.BackupException;
import com.ivan.nexus.application.backup.PostgresDumpResult;
import com.ivan.nexus.application.database.DiscoverProjectDatabases;
import com.ivan.nexus.application.database.InstanceResolution;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PgDumpBackupProviderTest {

    @TempDir
    Path tempDir;

    @Mock
    DiscoverProjectDatabases databases;

    private NexusProperties.Backup settings;

    @BeforeEach
    void setUp() {
        settings = new NexusProperties.Backup();
        settings.setLocalPath(tempDir.resolve("backups").toString());
        settings.setPgDumpExecutable("pg_dump");
    }

    @Test
    void dumpsFirstReadyPostgresToLocalPath() throws Exception {
        DatabaseInstance instance = new DatabaseInstance(
                "lab:abc", "lab", "cid", "db", DatabaseEngine.POSTGRES, DatabaseStatus.READY, "app");
        ResolvedTarget target = new ResolvedTarget("127.0.0.1", 5432, "nexus", "secret", "app");
        given(databases.execute("lab")).willReturn(List.of(instance));
        given(databases.resolve("lab", "lab:abc")).willReturn(new InstanceResolution(instance, target));

        AtomicReference<PgDumpBackupProvider.DumpCommand> captured = new AtomicReference<>();
        PgDumpBackupProvider provider = new PgDumpBackupProvider(databases, settings, command -> {
            captured.set(command);
            try {
                Path file = Path.of(command.argv().stream()
                        .filter(arg -> arg.startsWith("--file="))
                        .map(arg -> arg.substring("--file=".length()))
                        .findFirst()
                        .orElseThrow());
                Files.createDirectories(file.getParent());
                Files.writeString(file, "DUMP");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return new PgDumpBackupProvider.ProcessResult(0, "", "");
        });

        PostgresDumpResult result = provider.dumpPostgres("lab");

        assertThat(captured.get().password()).isEqualTo("secret");
        assertThat(captured.get().argv()).contains(
                "pg_dump",
                "--host=127.0.0.1",
                "--port=5432",
                "--username=nexus",
                "--dbname=app",
                "--no-password",
                "--format=custom");
        assertThat(result.sizeBytes()).isEqualTo(4);
        assertThat(result.databaseName()).isEqualTo("app");
        assertThat(result.artifactUri()).startsWith("file:");
        assertThat(Path.of(java.net.URI.create(result.artifactUri()))).exists();
    }

    @Test
    void failsWhenNoPostgresReady() {
        given(databases.execute("lab")).willReturn(List.of());

        PgDumpBackupProvider provider = new PgDumpBackupProvider(
                databases,
                settings,
                command -> new PgDumpBackupProvider.ProcessResult(0, "", ""));

        assertThatThrownBy(() -> provider.dumpPostgres("lab"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("No reachable Postgres");
    }

    @Test
    void failsWhenPgDumpExitsNonZero() {
        DatabaseInstance instance = new DatabaseInstance(
                "lab:abc", "lab", "cid", "db", DatabaseEngine.POSTGRES, DatabaseStatus.READY, "app");
        ResolvedTarget target = new ResolvedTarget("127.0.0.1", 5432, "nexus", "secret", "app");
        given(databases.execute("lab")).willReturn(List.of(instance));
        given(databases.resolve("lab", "lab:abc")).willReturn(new InstanceResolution(instance, target));

        PgDumpBackupProvider provider = new PgDumpBackupProvider(
                databases,
                settings,
                command -> new PgDumpBackupProvider.ProcessResult(1, "", "connection refused"));

        assertThatThrownBy(() -> provider.dumpPostgres("lab"))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("pg_dump exited with code 1");
    }
}
