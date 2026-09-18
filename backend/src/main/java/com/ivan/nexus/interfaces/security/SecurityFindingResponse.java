package com.ivan.nexus.interfaces.security;

import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;

import java.time.Instant;
import java.util.UUID;

public record SecurityFindingResponse(
        UUID id,
        String projectId,
        SecuritySeverity severity,
        String source,
        String packageName,
        String installedVersion,
        String fixedVersion,
        String title,
        String fingerprint,
        SecurityFindingStatus status,
        Instant firstSeen,
        Instant lastSeen) {

    public static SecurityFindingResponse from(SecurityFinding finding) {
        return new SecurityFindingResponse(
                finding.id(),
                finding.projectId(),
                finding.severity(),
                finding.source(),
                finding.packageName(),
                finding.installedVersion(),
                finding.fixedVersion(),
                finding.title(),
                finding.fingerprint(),
                finding.status(),
                finding.firstSeen(),
                finding.lastSeen());
    }
}
