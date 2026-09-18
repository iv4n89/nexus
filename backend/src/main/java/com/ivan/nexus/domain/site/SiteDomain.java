package com.ivan.nexus.domain.site;

import java.time.Instant;
import java.util.UUID;

/**
 * Managed hostname → service mapping. Shared with Phase C1 (PR #38);
 * included here so the Caddy adapter can compile before domains land on main.
 */
public record SiteDomain(
        UUID id,
        String projectId,
        String hostname,
        String serviceName,
        int targetPort,
        Instant createdAt,
        Instant updatedAt,
        CertStatus certStatus) {
}
