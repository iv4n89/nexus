package com.ivan.nexus.domain.site;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts candidate hostnames from project {@code .env} values and container labels.
 */
public final class DomainHostExtractor {
    private static final Pattern TRAEFIK_HOST = Pattern.compile(
            "Host\\s*\\(\\s*[`'\"]([^`'\"]+)[`'\"]\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final String[] ENV_KEYS = {
            "DOMAIN", "DOMAINS", "VIRTUAL_HOST", "NEXUS_DOMAIN", "HOST", "PUBLIC_HOST"
    };

    private DomainHostExtractor() {
    }

    public static Set<String> fromEnv(Map<String, String> env) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (env == null) {
            return hosts;
        }
        for (String key : ENV_KEYS) {
            String raw = env.get(key);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split("[,\\s]+")) {
                addIfValid(hosts, part);
            }
        }
        return hosts;
    }

    public static Set<String> fromLabels(Map<String, String> labels) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (labels == null) {
            return hosts;
        }
        addIfValid(hosts, labels.get("nexus.domain"));
        addIfValid(hosts, labels.get("caddy"));
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith("caddy_") || lower.startsWith("caddy.")) {
                addIfValid(hosts, entry.getValue());
            }
            if (lower.startsWith("traefik.http.routers.") && lower.endsWith(".rule")) {
                Matcher matcher = TRAEFIK_HOST.matcher(entry.getValue() == null ? "" : entry.getValue());
                while (matcher.find()) {
                    addIfValid(hosts, matcher.group(1));
                }
            }
        }
        return hosts;
    }

    public static Set<String> fromCaddy(String content) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return hosts;
        }
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("}")) {
                continue;
            }
            int brace = line.indexOf('{');
            if (brace <= 0) {
                continue;
            }
            String site = line.substring(0, brace).trim();
            if (site.isEmpty() || site.contains(" ")) {
                continue;
            }
            addIfValid(hosts, site);
        }
        return hosts;
    }

    /** First non-empty hostname wins per normalized key (insertion order preserved). */
    public static Map<String, String> mergePreferFirst(Set<String>... sources) {
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        if (sources == null) {
            return ordered;
        }
        for (Set<String> source : sources) {
            if (source == null) {
                continue;
            }
            for (String host : source) {
                ordered.putIfAbsent(host, host);
            }
        }
        return ordered;
    }

    private static void addIfValid(Set<String> hosts, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String candidate = raw.trim();
        if (candidate.contains("://")) {
            int scheme = candidate.indexOf("://");
            candidate = candidate.substring(scheme + 3);
        }
        int slash = candidate.indexOf('/');
        if (slash >= 0) {
            candidate = candidate.substring(0, slash);
        }
        int colon = candidate.indexOf(':');
        if (colon >= 0) {
            candidate = candidate.substring(0, colon);
        }
        try {
            String normalized = SiteHostname.normalizeAndValidate(candidate);
            if (!normalized.contains(".")) {
                return;
            }
            hosts.add(normalized);
        } catch (RuntimeException ignored) {
            // skip invalid host tokens
        }
    }
}
