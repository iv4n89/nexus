package com.ivan.nexus.application.env;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Project {@code .env} file as source of truth for environment variables.
 */
public interface ProjectDotEnvStore {
    /**
     * Reads the project {@code .env}. Missing file yields an empty ordered map.
     */
    Map<String, String> read(String projectId);

    /**
     * Atomically rewrites the project {@code .env} with the given ordered entries.
     */
    void write(String projectId, Map<String, String> values);

    /**
     * Directory that contains the project's {@code .env}, if one can be resolved.
     */
    default Optional<Path> locateDirectory(String projectId) {
        return Optional.empty();
    }

    /**
     * Caddy site file contents next to the project (for hostname discovery).
     */
    default List<String> caddySnippets(String projectId) {
        return List.of();
    }
}
