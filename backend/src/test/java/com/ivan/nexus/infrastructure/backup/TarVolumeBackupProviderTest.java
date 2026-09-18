package com.ivan.nexus.infrastructure.backup;

import com.ivan.nexus.application.backup.VolumeBackupResult;
import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TarVolumeBackupProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsDockerTarCommandAndReturnsArtifact() {
        NexusProperties.Backup settings = new NexusProperties.Backup();
        settings.setLocalPath(tempDir.resolve("backups").toString());
        AtomicReference<List<String>> captured = new AtomicReference<>();

        TarVolumeBackupProvider provider = new TarVolumeBackupProvider(settings, argv -> {
            captured.set(argv);
            int czf = argv.indexOf("czf");
            String outName = argv.get(czf + 1).substring("/out/".length());
            Path artifact = Path.of(settings.getLocalPath()).resolve("lab").resolve(outName);
            try {
                Files.createDirectories(artifact.getParent());
                Files.writeString(artifact, "tar");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return new TarVolumeBackupProvider.ProcessResult(0, "", "");
        });

        VolumeBackupResult result = provider.backupVolumes("lab", List.of("lab_data", "lab_uploads"));

        assertThat(captured.get()).contains("docker", "run", "--rm", "alpine:3.20", "tar", "czf");
        assertThat(captured.get()).anyMatch(arg -> arg.startsWith("lab_data:/backup/lab_data"));
        assertThat(result.volumes()).containsExactly("lab_data", "lab_uploads");
        assertThat(result.sizeBytes()).isEqualTo(3L);
        assertThat(result.artifactUri()).startsWith("file:");
    }
}
