package com.ivan.nexus.application.caddy;

import com.ivan.nexus.application.site.DomainStore;
import com.ivan.nexus.domain.site.SiteDomain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Regenerates the Caddy snippet for a project after domain CRUD.
 * Optional hook: call from AddDomain/RemoveDomain once Phase C1 is merged.
 */
@Service
@ConditionalOnProperty(name = "nexus.caddy.enabled", havingValue = "true")
@ConditionalOnBean(DomainStore.class)
public class ReloadProjectDomains {
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
        List<SiteDomain> projectDomains = domains.findByProjectId(projectId);
        caddyConfigWriter.writeProjectSites(projectId, projectDomains);
        for (SiteDomain domain : projectDomains) {
            certificateManager.ensureCertificate(domain);
        }
    }
}
