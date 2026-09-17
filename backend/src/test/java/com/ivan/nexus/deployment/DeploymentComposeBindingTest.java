package com.ivan.nexus.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentComposeBindingTest {

    @Test
    void frontendIsNotPublishedOnAllInterfaces() throws IOException {
        Path compose = Path.of("deployment/docker-compose.yml");
        if (!Files.isRegularFile(compose)) {
            compose = Path.of("..", "deployment", "docker-compose.yml");
        }
        String text = Files.readString(compose);
        assertThat(text).doesNotContain("\"3000:3000\"");
        assertThat(text).doesNotContain("'3000:3000'");
        assertThat(text).doesNotContain("- 3000:3000");
    }
}
