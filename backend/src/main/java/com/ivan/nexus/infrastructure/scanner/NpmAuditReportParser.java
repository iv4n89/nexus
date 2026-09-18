package com.ivan.nexus.infrastructure.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.domain.security.SecuritySeverity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code npm audit --json} output into normalized findings.
 */
public final class NpmAuditReportParser {
    private static final String SOURCE = "npm-audit";

    private final ObjectMapper objectMapper;

    public NpmAuditReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawSecurityFinding> parse(InputStream json) {
        try {
            return parse(objectMapper.readTree(json));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse npm audit JSON", ex);
        }
    }

    public List<RawSecurityFinding> parse(String json) {
        try {
            return parse(objectMapper.readTree(json));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse npm audit JSON", ex);
        }
    }

    List<RawSecurityFinding> parse(JsonNode root) {
        List<RawSecurityFinding> findings = new ArrayList<>();
        JsonNode vulnerabilities = root.path("vulnerabilities");
        if (!vulnerabilities.isObject()) {
            return List.of();
        }
        Iterator<Map.Entry<String, JsonNode>> fields = vulnerabilities.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode vuln = entry.getValue();
            String packageName = firstNonBlank(text(vuln, "name"), entry.getKey());
            if (packageName == null) {
                continue;
            }
            String title = firstNonBlank(advisoryTitle(vuln), packageName + " vulnerability");
            findings.add(new RawSecurityFinding(
                    mapSeverity(text(vuln, "severity")),
                    SOURCE,
                    packageName,
                    installedVersion(vuln),
                    fixedVersion(vuln),
                    title));
        }
        return List.copyOf(findings);
    }

    private static String advisoryTitle(JsonNode vuln) {
        JsonNode via = vuln.path("via");
        if (via.isArray() && !via.isEmpty()) {
            JsonNode first = via.get(0);
            if (first.isObject()) {
                return firstNonBlank(text(first, "title"), text(first, "url"), text(first, "source"));
            }
            if (first.isTextual()) {
                return first.asText();
            }
        }
        return null;
    }

    private static String installedVersion(JsonNode vuln) {
        JsonNode range = vuln.get("range");
        if (range != null && range.isTextual() && !range.asText().isBlank()) {
            return range.asText();
        }
        return null;
    }

    private static String fixedVersion(JsonNode vuln) {
        JsonNode fix = vuln.path("fixAvailable");
        if (fix.isBoolean() && !fix.asBoolean()) {
            return null;
        }
        if (fix.isObject()) {
            return blankToNull(text(fix, "version"));
        }
        JsonNode nodes = vuln.path("via");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (node.isObject()) {
                    String range = text(node, "range");
                    if (range != null && range.startsWith(">=")) {
                        return range.substring(2).trim();
                    }
                }
            }
        }
        return null;
    }

    static SecuritySeverity mapSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return SecuritySeverity.INFO;
        }
        return switch (severity.trim().toLowerCase(Locale.ROOT)) {
            case "critical" -> SecuritySeverity.CRITICAL;
            case "high" -> SecuritySeverity.HIGH;
            case "moderate", "medium" -> SecuritySeverity.MEDIUM;
            case "low" -> SecuritySeverity.LOW;
            default -> SecuritySeverity.INFO;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
