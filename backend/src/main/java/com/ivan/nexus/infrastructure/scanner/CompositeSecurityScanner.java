package com.ivan.nexus.infrastructure.scanner;

import com.ivan.nexus.application.security.RawSecurityFinding;
import com.ivan.nexus.application.security.SecurityScanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs multiple {@link SecurityScanner} adapters and concatenates findings.
 */
public class CompositeSecurityScanner implements SecurityScanner {
    private final List<SecurityScanner> delegates;

    public CompositeSecurityScanner(List<SecurityScanner> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public List<RawSecurityFinding> scan(String projectId) {
        List<RawSecurityFinding> findings = new ArrayList<>();
        for (SecurityScanner delegate : delegates) {
            findings.addAll(delegate.scan(projectId));
        }
        return List.copyOf(findings);
    }

    List<SecurityScanner> delegates() {
        return delegates;
    }
}
