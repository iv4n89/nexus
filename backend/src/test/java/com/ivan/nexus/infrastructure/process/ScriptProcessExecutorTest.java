package com.ivan.nexus.infrastructure.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
                line -> {},
                Duration.ofMillis(200));

        assertThat(exit).isEqualTo(124);
    }
}
