package com.ivan.nexus.infrastructure.scanner;

import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.application.security.SecurityScanner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder scanner until Trivy (Phase E2) is wired.
 */
@Component
public class NoOpSecurityScanner implements SecurityScanner {
    @Override
    public List<RawSecurityFinding> scan(String projectId) {
        return List.of();
    }
}
