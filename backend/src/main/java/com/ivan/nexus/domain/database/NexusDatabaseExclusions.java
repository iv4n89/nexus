package com.ivan.nexus.domain.database;

import java.util.Locale;
import java.util.Map;

public final class NexusDatabaseExclusions {
    private NexusDatabaseExclusions() {}

    public static boolean skip(String image, Map<String, String> labels) {
        Map<String, String> values = labels == null ? Map.of() : labels;
        if ("nexus".equalsIgnoreCase(values.getOrDefault("nexus.project", ""))) {
            return true;
        }
        if ("nexus".equalsIgnoreCase(values.getOrDefault("com.docker.compose.project", ""))) {
            return true;
        }
        if (image == null) {
            return false;
        }
        String lower = image.toLowerCase(Locale.ROOT);
        return lower.contains("nexus-backend") || lower.contains("nexus-postgres");
    }
}
