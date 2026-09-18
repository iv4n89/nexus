package com.ivan.nexus.domain.site;

import java.time.Instant;
import java.util.UUID;

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
