package com.ivan.nexus.domain.database;

import java.util.Locale;
import java.util.Optional;

public final class EngineDetector {
    private EngineDetector() {}

    public static Optional<DatabaseEngine> fromImage(String image) {
        if (image == null || image.isBlank()) {
            return Optional.empty();
        }
        String name = image;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(0, colon);
        }
        String token = name.toLowerCase(Locale.ROOT);
        if (token.contains("postgresql") || token.contains("postgres")) {
            return Optional.of(DatabaseEngine.POSTGRES);
        }
        if (token.contains("mariadb") || token.contains("mysql")) {
            return Optional.of(DatabaseEngine.MYSQL);
        }
        if (token.contains("mongodb") || token.equals("mongo") || token.startsWith("mongo")) {
            return Optional.of(DatabaseEngine.MONGO);
        }
        return Optional.empty();
    }
}
