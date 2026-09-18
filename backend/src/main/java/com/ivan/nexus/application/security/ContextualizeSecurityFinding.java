package com.ivan.nexus.application.security;

import com.ivan.nexus.domain.security.SecurityFinding;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Asks an {@link LlmClient} to summarize an existing security finding.
 * Never remediates or modifies project code.
 */
@Service
public class ContextualizeSecurityFinding {
    private final SecurityFindingStore findings;
    private final LlmClient llmClient;

    public ContextualizeSecurityFinding(SecurityFindingStore findings, LlmClient llmClient) {
        this.findings = findings;
        this.llmClient = llmClient;
    }

    @Transactional(readOnly = true)
    public Result execute(String projectId, UUID findingId) {
        SecurityFinding finding = findings
                .findById(findingId)
                .filter(f -> projectId.equals(f.projectId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));

        String prompt = buildPrompt(finding);
        String summary = llmClient.summarize(prompt);
        return new Result(finding.id(), finding.projectId(), summary);
    }

    static String buildPrompt(SecurityFinding finding) {
        return """
                Summarize this security finding for an operator. Do not suggest automated code changes.
                Severity: %s
                Source: %s
                Package: %s
                Installed: %s
                Fixed: %s
                Title: %s
                """.formatted(
                        finding.severity(),
                        finding.source(),
                        nullToDash(finding.packageName()),
                        nullToDash(finding.installedVersion()),
                        nullToDash(finding.fixedVersion()),
                        finding.title());
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record Result(UUID findingId, String projectId, String summary) {}
}
