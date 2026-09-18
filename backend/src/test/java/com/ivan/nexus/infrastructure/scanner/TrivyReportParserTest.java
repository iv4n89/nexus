package com.ivan.nexus.infrastructure.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.domain.security.SecuritySeverity;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrivyReportParserTest {

    private final TrivyReportParser parser = new TrivyReportParser(new ObjectMapper());

    @Test
    void parsesFixtureJsonIntoRawFindings() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/trivy/sample-report.json")) {
            assertThat(in).isNotNull();
            List<RawSecurityFinding> findings = parser.parse(in);

            assertThat(findings).containsExactly(
                    new RawSecurityFinding(
                            SecuritySeverity.HIGH,
                            "trivy",
                            "openssl",
                            "1.0.0",
                            "1.0.1",
                            "CVE-2024-1"),
                    new RawSecurityFinding(
                            SecuritySeverity.MEDIUM,
                            "trivy",
                            "curl",
                            "8.0.0",
                            null,
                            "CVE-2023-999"),
                    new RawSecurityFinding(
                            SecuritySeverity.INFO,
                            "trivy",
                            "left-pad",
                            "1.0.0",
                            null,
                            "GHSA-xxxx"));
        }
    }

    @Test
    void emptyResultsYieldEmptyList() {
        assertThat(parser.parse("{\"Results\":[]}")).isEmpty();
        assertThat(parser.parse("{}")).isEmpty();
    }

    @Test
    void mapsSeverityLevels() {
        assertThat(TrivyReportParser.mapSeverity("CRITICAL")).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(TrivyReportParser.mapSeverity("high")).isEqualTo(SecuritySeverity.HIGH);
        assertThat(TrivyReportParser.mapSeverity("MEDIUM")).isEqualTo(SecuritySeverity.MEDIUM);
        assertThat(TrivyReportParser.mapSeverity("LOW")).isEqualTo(SecuritySeverity.LOW);
        assertThat(TrivyReportParser.mapSeverity("UNKNOWN")).isEqualTo(SecuritySeverity.INFO);
        assertThat(TrivyReportParser.mapSeverity(null)).isEqualTo(SecuritySeverity.INFO);
    }
}
