package com.ivan.nexus.application.site;

import com.ivan.nexus.application.caddy.CertificateManager;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckDomainStatus {
    private static final Logger log = LoggerFactory.getLogger(CheckDomainStatus.class);

    private final DomainStore domains;
    private final DomainDnsResolver dnsResolver;
    private final Optional<DomainHttpsProbe> httpsProbe;
    private final Optional<CertificateManager> certificateManager;

    public CheckDomainStatus(
            DomainStore domains,
            DomainDnsResolver dnsResolver,
            Optional<DomainHttpsProbe> httpsProbe,
            Optional<CertificateManager> certificateManager) {
        this.domains = domains;
        this.dnsResolver = dnsResolver;
        this.httpsProbe = httpsProbe;
        this.certificateManager = certificateManager;
    }

    @Transactional
    public SiteDomain execute(UUID domainId) {
        SiteDomain domain = domains.findById(domainId)
                .orElseThrow(() -> new DomainException(NexusErrorCode.DOMAIN_NOT_FOUND, "Domain not found"));
        return execute(domain);
    }

    @Transactional
    public SiteDomain execute(SiteDomain domain) {
        CertStatus status = resolveStatus(domain);
        if (status == domain.certStatus()) {
            return domain;
        }
        SiteDomain updated = new SiteDomain(
                domain.id(),
                domain.projectId(),
                domain.hostname(),
                domain.serviceName(),
                domain.targetPort(),
                domain.createdAt(),
                Instant.now(),
                status);
        return domains.save(updated);
    }

    private CertStatus resolveStatus(SiteDomain domain) {
        if (!dnsResolver.resolves(domain.hostname())) {
            log.debug("DNS lookup failed for {}", domain.hostname());
            return CertStatus.ERROR;
        }

        CertStatus fromCert = certificateManager
                .map(manager -> manager.ensureCertificate(domain))
                .orElse(CertStatus.PENDING);

        if (httpsProbe.isEmpty()) {
            return fromCert == CertStatus.UNKNOWN ? CertStatus.PENDING : fromCert;
        }

        boolean httpsOk = httpsProbe.get().probe(domain.hostname());
        if (httpsOk) {
            return CertStatus.ACTIVE;
        }
        if (fromCert == CertStatus.ACTIVE || fromCert == CertStatus.PENDING) {
            return CertStatus.PENDING;
        }
        return fromCert == CertStatus.ERROR ? CertStatus.ERROR : CertStatus.PENDING;
    }
}
