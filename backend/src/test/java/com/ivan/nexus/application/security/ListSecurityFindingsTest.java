package com.ivan.nexus.application.security;

import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ListSecurityFindingsTest {

    @Mock
    SecurityFindingStore findings;

    @Test
    void delegatesToStore() {
        SecurityFinding finding = new SecurityFinding(
                UUID.randomUUID(),
                "lab",
                SecuritySeverity.LOW,
                "trivy",
                "pkg",
                "1",
                null,
                "title",
                "fp",
                SecurityFindingStatus.OPEN,
                Instant.parse("2026-09-18T00:00:00Z"),
                Instant.parse("2026-09-18T00:00:00Z"));
        given(findings.listByProject("lab")).willReturn(List.of(finding));

        assertThat(new ListSecurityFindings(findings).execute("lab")).containsExactly(finding);
        verify(findings).listByProject("lab");
    }
}
