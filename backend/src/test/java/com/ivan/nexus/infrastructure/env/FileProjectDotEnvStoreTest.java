package com.ivan.nexus.infrastructure.env;

import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import com.ivan.nexus.domain.manifest.ProjectManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileProjectDotEnvStoreTest {

    @TempDir
    Path allowedRoot;

    @Test
    void readWriteRoundTripPreservesOrder() throws Exception {
        Path projectDir = allowedRoot.resolve("lab");
        Files.createDirectories(projectDir);
        FakeManifestCatalog manifests = new FakeManifestCatalog()
                .add("lab", manifest(projectDir), projectDir.resolve("nexus.yml"));
        FileProjectDotEnvStore store = new FileProjectDotEnvStore(manifests, allowedRoot);

        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("NODE_ENV", "production");
        values.put("API_KEY", "secret");
        store.write("lab", values);

        assertThat(Files.readString(projectDir.resolve(".env")))
                .isEqualTo("NODE_ENV=production\nAPI_KEY=secret\n");
        assertThat(store.read("lab")).containsExactly(
                Map.entry("NODE_ENV", "production"),
                Map.entry("API_KEY", "secret"));
    }

    private static ProjectManifest manifest(Path workingDirectory) {
        return new ProjectManifest(
                new ProjectManifest.ProjectBlock(
                        "lab", "Lab", null, workingDirectory.toAbsolutePath().toString()),
                List.of(),
                new ProjectManifest.CommandBlock("./deploy.sh"),
                null,
                null,
                null);
    }
}
