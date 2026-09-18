package com.ivan.nexus.application.deployment;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface ProcessExecutor {
    int run(
            Path workingDirectory,
            List<String> argv,
            Map<String, String> environment,
            Consumer<String> onLine,
            Duration timeout);
}
