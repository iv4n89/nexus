package com.ivan.nexus.infrastructure.process;

import com.ivan.nexus.application.deployment.ProcessExecutor;
import com.ivan.nexus.infrastructure.database.SecretSanitizer;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class ScriptProcessExecutor implements ProcessExecutor {
    static final int TIMEOUT_EXIT_CODE = 124;

    @Override
    public int run(
            Path workingDirectory,
            List<String> argv,
            Map<String, String> environment,
            Consumer<String> onLine,
            Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        if (environment != null && !environment.isEmpty()) {
            builder.environment().putAll(environment);
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            String safe = SecretSanitizer.stripAll(
                    environment == null ? List.of() : environment.values(),
                    "Failed to start process: " + ex.getMessage());
            throw new UncheckedIOException(safe, ex);
        }

        Thread reader = Thread.ofVirtual().start(() -> drain(process, onLine));
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                return TIMEOUT_EXIT_CODE;
            }
            reader.join(timeout.toMillis());
            return process.exitValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Process interrupted", ex);
        }
    }

    private static void drain(Process process, Consumer<String> onLine) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                onLine.accept(line);
            }
        } catch (IOException ignored) {
            // Stream closed when the process is destroyed.
        }
    }
}
