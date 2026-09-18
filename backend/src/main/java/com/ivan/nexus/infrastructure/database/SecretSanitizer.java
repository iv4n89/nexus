package com.ivan.nexus.infrastructure.database;

import java.util.Collection;

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
        return redactUris(out);
    }

    public static String stripAll(Collection<String> secrets, String message) {
        if (message == null) {
            return "";
        }
        String out = message;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret != null && !secret.isBlank()) {
                    out = out.replace(secret, "***");
                }
            }
        }
        return redactUris(out);
    }

    private static String redactUris(String message) {
        String out = message.replaceAll("jdbc:[^\\s]+", "[uri]");
        return out.replaceAll("mongodb(\\+srv)?://[^\\s]+", "[uri]");
    }
}
