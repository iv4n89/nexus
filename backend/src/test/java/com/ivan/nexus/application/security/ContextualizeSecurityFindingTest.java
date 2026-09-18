package com.ivan.nexus.application.security;

import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContextualizeSecurityFindingTest {

    @Mock
    SecurityFindingStore findings;
    @Mock
    LlmClient llmClient;

    @Test
    void summarizesExistingFindingWithoutRemediation() {
        UUID id = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        SecurityFinding finding = new SecurityFinding(
                id,
                "lab",
                SecuritySeverity.HIGH,
                "npm-audit",
                "lodash",
                "4.17.0",
                "4.17.21",
                "Prototype Pollution",
                "abc",
                SecurityFindingStatus.OPEN,
                Instant.parse("2026-09-18T00:00:00Z"),
                Instant.parse("2026-09-18T00:00:00Z"));
        given(findings.findById(id)).willReturn(Optional.of(finding));
        given(llmClient.summarize(org.mockito.ArgumentMatchers.anyString()))
                .willReturn("lodash is vulnerable; upgrade when convenient.");

        ContextualizeSecurityFinding.Result result =
                new ContextualizeSecurityFinding(findings, llmClient).execute("lab", id);

        assertThat(result.findingId()).isEqualTo(id);
        assertThat(result.summary()).contains("lodash");
        verify(llmClient).summarize(org.mockito.ArgumentMatchers.contains("Prototype Pollution"));
        verify(llmClient).summarize(org.mockito.ArgumentMatchers.contains("Do not suggest automated code changes"));
    }
}
