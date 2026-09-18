package com.ivan.nexus.domain.env;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classic dotenv parse/format without shell expansion.
 */
public final class DotEnvParser {
    private static final Pattern KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private DotEnvParser() {
    }

    public static Map<String, String> parse(String content) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return values;
        }
        for (String rawLine : content.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (!KEY.matcher(key).matches()) {
                continue;
            }
            String value = unquote(line.substring(eq + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    public static String format(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || !KEY.matcher(entry.getKey()).matches()) {
                continue;
            }
            out.append(entry.getKey()).append('=').append(quoteIfNeeded(entry.getValue())).append('\n');
        }
        return out.toString();
    }

    public static boolean looksSecret(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("SECRET")
                || upper.contains("PASSWORD")
                || upper.contains("TOKEN")
                || upper.contains("DATABASE_URL")
                || upper.endsWith("_KEY")
                || upper.contains("_KEY_")
                || upper.equals("API_KEY")
                || upper.contains("PRIVATE");
    }

    static String unquote(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                String inner = value.substring(1, value.length() - 1);
                if (first == '"') {
                    return inner
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\t", "\t")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                }
                return inner;
            }
        }
        int hash = value.indexOf(" #");
        if (hash >= 0) {
            return value.substring(0, hash).trim();
        }
        return value;
    }

    static String quoteIfNeeded(String value) {
        if (value == null) {
            return "";
        }
        if (value.isEmpty()) {
            return "\"\"";
        }
        boolean needsQuotes = value.indexOf(' ') >= 0
                || value.indexOf('#') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\t') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\'') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
