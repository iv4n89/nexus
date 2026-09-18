package com.ivan.nexus.infrastructure.caddy;

import com.ivan.nexus.application.caddy.CertificateManager;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Light certificate manager: Caddy obtains ACME certs when site blocks are loaded.
 * Returns {@link CertStatus#PENDING} until a later health-check slice updates status.
 */
@Component
@ConditionalOnProperty(name = "nexus.caddy.enabled", havingValue = "true")
public class CaddyManagedCertificateManager implements CertificateManager {
    private static final Logger log = LoggerFactory.getLogger(CaddyManagedCertificateManager.class);

    @Override
    public CertStatus ensureCertificate(SiteDomain domain) {
        log.debug("Deferring TLS for {} to Caddy ACME (status PENDING)", domain.hostname());
        return CertStatus.PENDING;
    }
}
