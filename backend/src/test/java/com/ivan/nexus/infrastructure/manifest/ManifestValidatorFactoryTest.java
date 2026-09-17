package com.ivan.nexus.infrastructure.manifest;

import com.ivan.nexus.infrastructure.config.NexusProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestValidatorFactoryTest {

    @Test
    void readsAllowedRootFromPropertiesAndNormalizes() {
        NexusProperties properties = new NexusProperties();
        properties.getManifest().setAllowedRoot("/tmp/foo/../nexus-root");

        Path root = new ManifestValidatorFactory().manifestAllowedRoot(properties);

        assertThat(root).isEqualTo(Path.of("/tmp/nexus-root").toAbsolutePath().normalize());
    }
}
