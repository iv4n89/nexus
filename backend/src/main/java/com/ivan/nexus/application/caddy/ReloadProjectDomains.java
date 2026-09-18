package com.ivan.nexus.application.caddy;

import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Regenerates the Caddy snippet for a project after domain CRUD.
 * Invoked optionally from {@link com.ivan.nexus.application.site.AddDomain}
 * and {@link com.ivan.nexus.application.site.RemoveDomain} when Caddy is enabled.
 */
@Service
@ConditionalOnProperty(name = "nexus.caddy.enabled", havingValue = "true")
@ConditionalOnBean(DomainStore.class)
public class ReloadProjectDomains {
    private static final Logger log = LoggerFactory.getLogger(ReloadProjectDomains.class);

    private final DomainStore domains;
    private final CaddyConfigWriter caddyConfigWriter;
    private final CertificateManager certificateManager;

    public ReloadProjectDomains(
            DomainStore domains,
            CaddyConfigWriter caddyConfigWriter,
            CertificateManager certificateManager) {
        this.domains = domains;
        this.caddyConfigWriter = caddyConfigWriter;
        this.certificateManager = certificateManager;
    }

    public void execute(String projectId) {
        try {
            List<SiteDomain> projectDomains = domains.findByProjectId(projectId);
            caddyConfigWriter.writeProjectSites(projectId, projectDomains);
            for (SiteDomain domain : projectDomains) {
                CertStatus status = certificateManager.ensureCertificate(domain);
                if (status != domain.certStatus()) {
                    domains.save(new SiteDomain(
                            domain.id(),
                            domain.projectId(),
                            domain.hostname(),
                            domain.serviceName(),
                            domain.targetPort(),
                            domain.createdAt(),
                            Instant.now(),
                            status));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Caddy reload failed for project {}: {}", projectId, ex.toString());
        }
    }
}
