package com.ivan.nexus.application.site;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.caddy.ReloadProjectDomains;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.site.CertStatus;
import com.ivan.nexus.domain.site.SiteDomain;
import com.ivan.nexus.domain.site.SiteHostname;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AddDomain {
    private final DomainStore domains;
    private final UserDirectory users;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final Optional<ReloadProjectDomains> reloadProjectDomains;

    public AddDomain(
            DomainStore domains,
            UserDirectory users,
            RecordAudit recordAudit,
            RecordActivity recordActivity,
            Optional<ReloadProjectDomains> reloadProjectDomains) {
        this.domains = domains;
        this.users = users;
        this.recordAudit = recordAudit;
        this.recordActivity = recordActivity;
        this.reloadProjectDomains = reloadProjectDomains;
    }

    @Transactional
    public SiteDomain execute(
            String projectId,
            String hostname,
            String serviceName,
            int targetPort,
            String username,
            String ip) {
        if (projectId == null || projectId.isBlank()) {
            throw new DomainException(NexusErrorCode.PROJECT_NOT_FOUND, "Project id is required");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Service name is required");
        }
        if (targetPort < 1 || targetPort > 65535) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "Target port must be between 1 and 65535");
        }

        String normalized = SiteHostname.normalizeAndValidate(hostname);
        domains.findByHostname(normalized).ifPresent(existing -> {
            throw new DomainException(
                    NexusErrorCode.DOMAIN_HOSTNAME_DUPLICATE,
                    "Hostname already registered: " + normalized);
        });

        Instant now = Instant.now();
        SiteDomain created = domains.save(new SiteDomain(
                UUID.randomUUID(),
                projectId,
                normalized,
                serviceName.trim(),
                targetPort,
                now,
                now,
                CertStatus.PENDING));

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of(
                "domainId", created.id().toString(),
                "hostname", created.hostname(),
                "serviceName", created.serviceName(),
                "targetPort", created.targetPort());
        recordAudit.execute(userId, AuditAction.DOMAIN_ADD, projectId, created.serviceName(), ip, metadata);
        recordActivity.execute(
                ActivityType.DOMAIN_ADDED,
                projectId,
                created.serviceName(),
                "Domain added: " + created.hostname(),
                metadata);
        reloadProjectDomains.ifPresent(reload -> reload.execute(projectId));
        return created;
    }
}
