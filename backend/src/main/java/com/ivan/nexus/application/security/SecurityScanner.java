package com.ivan.nexus.application.security;

import java.util.List;

/**
 * Outbound port for project security scanners (Trivy, OSV, etc.).
 */
public interface SecurityScanner {
    List<RawSecurityFinding> scan(String projectId);
}
