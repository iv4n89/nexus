package com.ivan.nexus.application.env;

import java.util.LinkedHashMap;
import java.util.Map;

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
}
