package com.ivan.nexus.infrastructure.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.domain.security.SecuritySeverity;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NpmAuditReportParserTest {

    @Test
    void parsesNpmAuditJson() throws Exception {
        String json = Files.readString(
                Path.of(getClass().getResource("/npm-audit/sample-report.json").toURI()));
        List<RawSecurityFinding> findings = new NpmAuditReportParser(new ObjectMapper()).parse(json);

        assertThat(findings).hasSize(2);
        assertThat(findings.getFirst().source()).isEqualTo("npm-audit");
        assertThat(findings.getFirst().packageName()).isEqualTo("lodash");
        assertThat(findings.getFirst().severity()).isEqualTo(SecuritySeverity.HIGH);
        assertThat(findings.getFirst().fixedVersion()).isEqualTo("4.17.21");
        assertThat(findings.get(1).severity()).isEqualTo(SecuritySeverity.CRITICAL);
    }
}
