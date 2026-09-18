package com.ivan.nexus.infrastructure.scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.domain.security.SecuritySeverity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses Trivy JSON report output into normalized raw findings.
 */
public final class TrivyReportParser {
    private static final String SOURCE = "trivy";

    private final ObjectMapper objectMapper;

    public TrivyReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawSecurityFinding> parse(InputStream json) {
        try {
            return parse(objectMapper.readTree(json));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse Trivy JSON report", ex);
        }
    }

    public List<RawSecurityFinding> parse(String json) {
        try {
            return parse(objectMapper.readTree(json));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to parse Trivy JSON report", ex);
        }
    }

    List<RawSecurityFinding> parse(JsonNode root) {
        List<RawSecurityFinding> findings = new ArrayList<>();
        JsonNode results = root.path("Results");
        if (!results.isArray()) {
            return List.of();
        }
        for (JsonNode result : results) {
            JsonNode vulnerabilities = result.path("Vulnerabilities");
            if (!vulnerabilities.isArray()) {
                continue;
            }
            for (JsonNode vuln : vulnerabilities) {
                String packageName = text(vuln, "PkgName");
                String title = firstNonBlank(text(vuln, "VulnerabilityID"), text(vuln, "Title"));
                if (packageName == null || title == null) {
                    continue;
                }
                findings.add(new RawSecurityFinding(
                        mapSeverity(text(vuln, "Severity")),
                        SOURCE,
                        packageName,
                        text(vuln, "InstalledVersion"),
                        blankToNull(text(vuln, "FixedVersion")),
                        title));
            }
        }
        return List.copyOf(findings);
    }

    static SecuritySeverity mapSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return SecuritySeverity.INFO;
        }
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> SecuritySeverity.CRITICAL;
            case "HIGH" -> SecuritySeverity.HIGH;
            case "MEDIUM" -> SecuritySeverity.MEDIUM;
            case "LOW" -> SecuritySeverity.LOW;
            default -> SecuritySeverity.INFO;
        };
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
