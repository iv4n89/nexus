package com.ivan.nexus.interfaces.site;

import com.ivan.nexus.application.site.AddDomain;
import com.ivan.nexus.application.site.ListProjectDomains;
import com.ivan.nexus.application.site.RemoveDomain;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import com.ivan.nexus.infrastructure.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/domains")
public class DomainController {
    private final ListProjectDomains listProjectDomains;
    private final AddDomain addDomain;
    private final RemoveDomain removeDomain;

    public DomainController(
            ListProjectDomains listProjectDomains,
            AddDomain addDomain,
            RemoveDomain removeDomain) {
        this.listProjectDomains = listProjectDomains;
        this.addDomain = addDomain;
        this.removeDomain = removeDomain;
    }

    @GetMapping
    public List<DomainResponse> list(@PathVariable String projectId) {
        return listProjectDomains.execute(projectId).stream()
                .map(DomainResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<DomainResponse> add(
            @PathVariable String projectId,
            @RequestBody AddDomainRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        SiteDomain created = addDomain.execute(
                projectId,
                body.hostname(),
                body.serviceName(),
                body.targetPort(),
                authentication.getName(),
                ClientIp.resolve(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(DomainResponse.from(created));
    }

    @DeleteMapping("/{domainId}")
    public ResponseEntity<Void> remove(
            @PathVariable String projectId,
            @PathVariable UUID domainId,
            Authentication authentication,
            HttpServletRequest request) {
        removeDomain.execute(projectId, domainId, authentication.getName(), ClientIp.resolve(request));
        return ResponseEntity.noContent().build();
    }

    public record AddDomainRequest(String hostname, String serviceName, int targetPort) {
    }

    public record DomainResponse(
            UUID id,
            String projectId,
            String hostname,
            String serviceName,
            int targetPort,
            Instant createdAt,
            Instant updatedAt,
            CertStatus certStatus) {
        static DomainResponse from(SiteDomain domain) {
            return new DomainResponse(
                    domain.id(),
                    domain.projectId(),
                    domain.hostname(),
                    domain.serviceName(),
                    domain.targetPort(),
                    domain.createdAt(),
                    domain.updatedAt(),
                    domain.certStatus());
        }
    }
}
