package com.ivan.nexus.domain.database;

import java.util.Map;

public final class EnvCredentialParser {
    private EnvCredentialParser() {}

    public static ParsedCredentials parse(DatabaseEngine engine, Map<String, String> env) {
        Map<String, String> values = env == null ? Map.of() : env;
        return switch (engine) {
            case POSTGRES -> postgres(values);
            case MYSQL -> mysql(values);
            case MONGO -> mongo(values);
        };
    }

    private static ParsedCredentials postgres(Map<String, String> env) {
        String username = first(env, "POSTGRES_USER", "postgres");
        String password = blankToNull(env.get("POSTGRES_PASSWORD"));
        String database = first(env, "POSTGRES_DB", "postgres");
        return new ParsedCredentials(username, password, database, password != null);
    }

    private static ParsedCredentials mysql(Map<String, String> env) {
        String user = firstNonBlank(env, "MYSQL_USER", "MARIADB_USER");
        String userPassword = firstNonBlank(env, "MYSQL_PASSWORD", "MARIADB_PASSWORD");
        String rootPassword = firstNonBlank(env, "MYSQL_ROOT_PASSWORD", "MARIADB_ROOT_PASSWORD");
        String database = first(env.get("MYSQL_DATABASE"), first(env.get("MARIADB_DATABASE"), "mysql"));
        if (user != null && userPassword != null) {
            return new ParsedCredentials(user, userPassword, database, true);
        }
        if (rootPassword != null) {
            return new ParsedCredentials("root", rootPassword, database, true);
        }
        return new ParsedCredentials(user != null ? user : "root", null, database, false);
    }

    private static ParsedCredentials mongo(Map<String, String> env) {
        String username = blankToNull(env.get("MONGO_INITDB_ROOT_USERNAME"));
        String password = blankToNull(env.get("MONGO_INITDB_ROOT_PASSWORD"));
        String database = first(env, "MONGO_INITDB_DATABASE", "test");
        boolean reachable = username == null || password != null;
        return new ParsedCredentials(username, password, database, reachable);
    }

    private static String first(Map<String, String> env, String key, String fallback) {
        return first(env.get(key), fallback);
    }

    private static String first(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed != null ? trimmed : fallback;
    }

    private static String firstNonBlank(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String value = blankToNull(env.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
