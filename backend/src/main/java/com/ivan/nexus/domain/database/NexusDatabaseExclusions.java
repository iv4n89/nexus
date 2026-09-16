package com.ivan.nexus.domain.database;

import java.util.Locale;
import java.util.Map;

public final class NexusDatabaseExclusions {
    private NexusDatabaseExclusions() {}

    public static boolean skip(String image, Map<String, String> labels) {
        if (image == null) {
            return false;
        }
        String lower = image.toLowerCase(Locale.ROOT);
        return lower.contains("nexus-backend");
    }
}
