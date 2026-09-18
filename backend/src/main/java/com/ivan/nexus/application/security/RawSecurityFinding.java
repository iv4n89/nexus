package com.ivan.nexus.application.security;

import com.ivan.nexus.domain.security.SecuritySeverity;

/**
 * Scanner output before fingerprinting and persistence.
 */
public record RawSecurityFinding(
        SecuritySeverity severity,
        String source,
        String packageName,
        String installedVersion,
        String fixedVersion,
        String title) {
}
