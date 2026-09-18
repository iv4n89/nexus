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
        if (token.equals("mongo") || token.equals("mongodb")) {
            return Optional.of(DatabaseEngine.MONGO);
        }
        return Optional.empty();
    }

    public static boolean looksLikeImageId(String image) {
        if (image == null || image.isBlank()) {
            return true;
        }
        String lower = image.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("sha256:")) {
            return true;
        }
        return lower.matches("[0-9a-f]{64}");
    }

    public static boolean looksLikeDatabaseService(String serviceOrName) {
        if (serviceOrName == null || serviceOrName.isBlank()) {
            return false;
        }
        String hay = serviceOrName.toLowerCase(Locale.ROOT);
        return hay.contains("postgres")
                || hay.contains("mysql")
                || hay.contains("mariadb")
                || hay.contains("mongo")
                || hay.contains("redis")
                || hay.equals("db")
                || hay.startsWith("db-")
                || hay.endsWith("-db")
                || hay.contains("_db")
                || hay.contains("-db-");
    }
}
