package com.ivan.nexus.application.project;

import com.ivan.nexus.application.deployment.ManagedProjectStore;
import com.ivan.nexus.application.env.ProjectDotEnvStore;
import com.ivan.nexus.application.manifest.FakeManifestCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnsureManagedProjectTest {

    @Mock
    ManagedProjectStore projects;

    @Test
    void usesLocatedDirectoryWhenThereIsNoManifest() {
        Path dir = Path.of("/host-opt/nexus");
        ProjectDotEnvStore env = new ProjectDotEnvStore() {
            @Override
            public Map<String, String> read(String projectId) {
                return Map.of();
            }

            @Override
            public void write(String projectId, Map<String, String> values) {
            }

            @Override
            public Optional<Path> locateDirectory(String projectId) {
                return Optional.of(dir);
            }
        };

        new EnsureManagedProject(projects, new FakeManifestCatalog(), env).execute("nexus");

        verify(projects).ensureRegistered("nexus", dir.toString(), dir.resolve("nexus.yml").toString());
    }
}
