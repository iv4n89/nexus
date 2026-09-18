package com.ivan.nexus.domain.security;

import java.time.Instant;
import java.util.UUID;

public record SecurityFinding(
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

    public SecurityFinding acknowledge(Instant at) {
        return new SecurityFinding(
                id,
                projectId,
                severity,
                source,
                packageName,
                installedVersion,
                fixedVersion,
                title,
                fingerprint,
                SecurityFindingStatus.ACKNOWLEDGED,
                firstSeen,
                lastSeen);
    }

    public SecurityFinding resolve(Instant at) {
        return new SecurityFinding(
                id,
                projectId,
                severity,
                source,
                packageName,
                installedVersion,
                fixedVersion,
                title,
                fingerprint,
                SecurityFindingStatus.RESOLVED,
                firstSeen,
                lastSeen);
    }

    public SecurityFinding seenAgain(
            SecuritySeverity newSeverity,
            String newSource,
            String newPackageName,
            String newInstalledVersion,
            String newFixedVersion,
            String newTitle,
            Instant at) {
        SecurityFindingStatus nextStatus =
                status == SecurityFindingStatus.RESOLVED ? SecurityFindingStatus.OPEN : status;
        return new SecurityFinding(
                id,
                projectId,
                newSeverity,
                newSource,
                newPackageName,
                newInstalledVersion,
                newFixedVersion,
                newTitle,
                fingerprint,
                nextStatus,
                firstSeen,
                at);
    }
}
