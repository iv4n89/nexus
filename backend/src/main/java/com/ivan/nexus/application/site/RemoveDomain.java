package com.ivan.nexus.application.site;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.application.caddy.ReloadProjectDomains;
import com.ivan.nexus.application.user.UserDirectory;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.audit.AuditAction;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.domain.site.SiteDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RemoveDomain {
    private final DomainStore domains;
    private final UserDirectory users;
    private final RecordAudit recordAudit;
    private final RecordActivity recordActivity;
    private final Optional<ReloadProjectDomains> reloadProjectDomains;

    public RemoveDomain(
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
    public void execute(String projectId, UUID domainId, String username, String ip) {
        SiteDomain domain = domains.findById(domainId)
                .orElseThrow(() -> new DomainException(NexusErrorCode.DOMAIN_NOT_FOUND, "Domain not found"));
        if (!domain.projectId().equals(projectId)) {
            throw new DomainException(NexusErrorCode.DOMAIN_NOT_FOUND, "Domain not found for project");
        }

        domains.delete(domainId);

        UUID userId = users.findIdByUsername(username).orElse(null);
        Map<String, Object> metadata = Map.of(
                "domainId", domain.id().toString(),
                "hostname", domain.hostname());
        recordAudit.execute(userId, AuditAction.DOMAIN_REMOVE, projectId, domain.serviceName(), ip, metadata);
        recordActivity.execute(
                ActivityType.DOMAIN_REMOVED,
                projectId,
                domain.serviceName(),
                "Domain removed: " + domain.hostname(),
                metadata);
        reloadProjectDomains.ifPresent(reload -> reload.execute(projectId));
    }
}
