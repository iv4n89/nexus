package com.ivan.nexus.infrastructure.scanner;

import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.application.security.SecurityScanner;

import java.util.List;

/**
 * Placeholder scanner used when Trivy is disabled.
 */
public class NoOpSecurityScanner implements SecurityScanner {
    @Override
    public List<RawSecurityFinding> scan(String projectId) {
        return List.of();
    }
}
