package com.ivan.nexus.infrastructure.database;

public final class SecretSanitizer {
    private SecretSanitizer() {}

    public static String strip(String secret, String message) {
        if (message == null) {
            return "Query failed";
        }
        String out = message;
        if (secret != null && !secret.isBlank()) {
            out = out.replace(secret, "***");
        }
        out = out.replaceAll("jdbc:[^\\s]+", "[uri]");
        out = out.replaceAll("mongodb(\\+srv)?://[^\\s]+", "[uri]");
        return out;
    }
}
