package com.ivan.nexus.domain.database;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Merges container env with project {@code .env} and parses common database URL forms.
 */
public final class DotEnvCredentialMerger {
    private DotEnvCredentialMerger() {
    }

    public static Map<String, String> merge(Map<String, String> containerEnv, Map<String, String> projectEnv) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        if (projectEnv != null) {
            merged.putAll(projectEnv);
        }
        if (containerEnv != null) {
            for (Map.Entry<String, String> entry : containerEnv.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    merged.put(entry.getKey(), entry.getValue());
                } else if (!merged.containsKey(entry.getKey())) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        applyDatabaseUrl(merged);
        return merged;
    }

    public static ParsedCredentials parseWithFallback(DatabaseEngine engine, Map<String, String> mergedEnv) {
        ParsedCredentials fromEnv = EnvCredentialParser.parse(engine, mergedEnv);
        if (fromEnv.reachable()) {
            return fromEnv;
        }
        UrlParts url = parseUrl(mergedEnv);
        if (url == null) {
            return fromEnv;
        }
        String username = firstNonBlank(fromEnv.username(), url.username(), defaultUser(engine));
        String password = firstNonBlank(fromEnv.password(), url.password());
        String database = firstNonBlank(fromEnv.defaultDatabase(), url.database(), defaultDb(engine));
        return new ParsedCredentials(username, password, database, password != null || engine == DatabaseEngine.MONGO);
    }

    public static HostHint hostHint(Map<String, String> mergedEnv) {
        String explicit = firstNonBlank(
                mergedEnv.get("DB_HOST"),
                mergedEnv.get("POSTGRES_HOST"),
                mergedEnv.get("MYSQL_HOST"),
                mergedEnv.get("DATABASE_HOST"));
        UrlParts url = parseUrl(mergedEnv);
        if (explicit != null) {
            Integer port = url == null ? null : url.port();
            return new HostHint(explicit, port);
        }
        if (url != null && url.host() != null) {
            return new HostHint(url.host(), url.port());
        }
        return null;
    }

    public static boolean isRoutableHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        return !(h.equals("localhost")
                || h.equals("127.0.0.1")
                || h.equals("::1")
                || h.equals("0.0.0.0")
                || h.endsWith(".local")
                || h.equals("db")
                || h.equals("postgres")
                || h.equals("mysql")
                || h.equals("mariadb")
                || h.equals("mongo")
                || h.equals("mongodb"));
    }

    private static void applyDatabaseUrl(Map<String, String> env) {
        UrlParts url = parseUrl(env);
        if (url == null) {
            return;
        }
        if (url.username() != null) {
            env.putIfAbsent("POSTGRES_USER", url.username());
            env.putIfAbsent("MYSQL_USER", url.username());
            env.putIfAbsent("MONGO_INITDB_ROOT_USERNAME", url.username());
        }
        if (url.password() != null) {
            env.putIfAbsent("POSTGRES_PASSWORD", url.password());
            env.putIfAbsent("MYSQL_PASSWORD", url.password());
            env.putIfAbsent("MYSQL_ROOT_PASSWORD", url.password());
            env.putIfAbsent("MONGO_INITDB_ROOT_PASSWORD", url.password());
        }
        if (url.database() != null) {
            env.putIfAbsent("POSTGRES_DB", url.database());
            env.putIfAbsent("MYSQL_DATABASE", url.database());
            env.putIfAbsent("MONGO_INITDB_DATABASE", url.database());
        }
        if (url.host() != null) {
            env.putIfAbsent("DB_HOST", url.host());
        }
    }

    static UrlParts parseUrl(Map<String, String> env) {
        if (env == null) {
            return null;
        }
        for (String key : new String[] {"DATABASE_URL", "POSTGRES_URL", "MYSQL_URL", "MONGO_URL"}) {
            UrlParts parts = parseUrl(env.get(key));
            if (parts != null) {
                return parts;
            }
        }
        return null;
    }

    static UrlParts parseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String normalized = raw.trim();
            if (normalized.startsWith("jdbc:")) {
                normalized = normalized.substring("jdbc:".length());
            }
            URI uri = URI.create(normalized);
            String userInfo = uri.getUserInfo();
            String username = null;
            String password = null;
            if (userInfo != null && !userInfo.isBlank()) {
                int colon = userInfo.indexOf(':');
                if (colon >= 0) {
                    username = decode(userInfo.substring(0, colon));
                    password = decode(userInfo.substring(colon + 1));
                } else {
                    username = decode(userInfo);
                }
            }
            String path = uri.getPath();
            String database = null;
            if (path != null && path.length() > 1) {
                database = path.substring(1);
                int q = database.indexOf('?');
                if (q >= 0) {
                    database = database.substring(0, q);
                }
            }
            Integer port = uri.getPort() > 0 ? uri.getPort() : null;
            return new UrlParts(uri.getHost(), port, username, password, database);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String defaultUser(DatabaseEngine engine) {
        return switch (engine) {
            case POSTGRES -> "postgres";
            case MYSQL -> "root";
            case MONGO -> null;
        };
    }

    private static String defaultDb(DatabaseEngine engine) {
        return switch (engine) {
            case POSTGRES -> "postgres";
            case MYSQL -> "mysql";
            case MONGO -> "test";
        };
    }

    public record HostHint(String host, Integer port) {}

    record UrlParts(String host, Integer port, String username, String password, String database) {}
}
