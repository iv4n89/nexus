package com.ivan.nexus.infrastructure.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptProcessExecutorTest {

    @TempDir
    Path workingDirectory;

    private final ScriptProcessExecutor executor = new ScriptProcessExecutor();

    @Test
    void echoStreamsHelloAndExitsZero() {
        List<String> lines = new ArrayList<>();

        int exit = executor.run(
                workingDirectory,
                List.of("/bin/echo", "hello"),
                Map.of(),
                lines::add,
                Duration.ofSeconds(5));

        assertThat(exit).isZero();
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst()).contains("hello");
    }

    @Test
    void timeoutReturns124() {
        int exit = executor.run(
                workingDirectory,
                List.of("/bin/sleep", "2"),
                Map.of(),
                line -> {},
                Duration.ofMillis(200));

        assertThat(exit).isEqualTo(124);
    }

    @Test
    void injectsEnvironmentVariables() {
        List<String> lines = new ArrayList<>();

        int exit = executor.run(
                workingDirectory,
                List.of("/bin/sh", "-c", "printf '%s\\n' \"$NEXUS_TEST_ENV\""),
                Map.of("NEXUS_TEST_ENV", "from-nexus"),
                lines::add,
                Duration.ofSeconds(5));

        assertThat(exit).isZero();
        assertThat(lines).containsExactly("from-nexus");
    }
}
