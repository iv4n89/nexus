package com.ivan.nexus.interfaces.security;

import com.ivan.nexus.application.security.AcknowledgeSecurityFinding;
import com.ivan.nexus.application.security.ContextualizeSecurityFinding;
import com.ivan.nexus.application.security.ListSecurityFindings;
import com.ivan.nexus.application.security.RunSecurityScan;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SecurityFindingController {
    private final ListSecurityFindings listSecurityFindings;
    private final RunSecurityScan runSecurityScan;
    private final AcknowledgeSecurityFinding acknowledgeSecurityFinding;
    private final ContextualizeSecurityFinding contextualizeSecurityFinding;

    public SecurityFindingController(
            ListSecurityFindings listSecurityFindings,
            RunSecurityScan runSecurityScan,
            AcknowledgeSecurityFinding acknowledgeSecurityFinding,
            ContextualizeSecurityFinding contextualizeSecurityFinding) {
        this.listSecurityFindings = listSecurityFindings;
        this.runSecurityScan = runSecurityScan;
        this.acknowledgeSecurityFinding = acknowledgeSecurityFinding;
        this.contextualizeSecurityFinding = contextualizeSecurityFinding;
    }

    @GetMapping("/api/projects/{id}/security/findings")
    public List<SecurityFindingResponse> list(@PathVariable("id") String projectId) {
        return listSecurityFindings.execute(projectId).stream()
                .map(SecurityFindingResponse::from)
                .toList();
    }

    @PostMapping("/api/projects/{id}/security/scan")
    public List<SecurityFindingResponse> scan(@PathVariable("id") String projectId) {
        return runSecurityScan.execute(projectId).stream()
                .map(SecurityFindingResponse::from)
                .toList();
    }

    @PostMapping("/api/projects/{id}/security/findings/{findingId}/acknowledge")
    public SecurityFindingResponse acknowledge(
            @PathVariable("id") String projectId,
            @PathVariable UUID findingId,
            Authentication authentication,
            HttpServletRequest request) {
        return SecurityFindingResponse.from(
                acknowledgeSecurityFinding.execute(
                        projectId, findingId, authentication.getName(), clientIp(request)));
    }

    @PostMapping("/api/projects/{id}/security/findings/{findingId}/contextualize")
    public ContextualizeResponse contextualize(
            @PathVariable("id") String projectId, @PathVariable UUID findingId) {
        ContextualizeSecurityFinding.Result result =
                contextualizeSecurityFinding.execute(projectId, findingId);
        return new ContextualizeResponse(result.findingId(), result.projectId(), result.summary());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record ContextualizeResponse(UUID findingId, String projectId, String summary) {}
}
