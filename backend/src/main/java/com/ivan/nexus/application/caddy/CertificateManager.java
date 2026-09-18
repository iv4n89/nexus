package com.ivan.nexus.application.caddy;

import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;

/**
 * Outbound port for TLS certificate provisioning/status (Caddy ACME in V1 light).
 */
public interface CertificateManager {
    /**
     * Ensures a certificate path exists for the domain. Light adapter defers to Caddy ACME
     * and returns the tracked status (typically {@link CertStatus#PENDING} until health checks).
     */
    CertStatus ensureCertificate(SiteDomain domain);
}
