package com.ivan.nexus.infrastructure.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalBackupStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storeReturnsFileUriAndDeleteRemovesArtifact() throws Exception {
        Path artifact = tempDir.resolve("lab").resolve("dump.dump");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "dump");

        LocalBackupStorage storage = new LocalBackupStorage(tempDir.resolve("root").toString());
        String uri = storage.store("lab", artifact.toString());

        assertThat(uri).startsWith("file:");
        assertThat(storage.exists(uri)).isTrue();

        storage.delete(uri);
        assertThat(storage.exists(uri)).isFalse();
    }
}
