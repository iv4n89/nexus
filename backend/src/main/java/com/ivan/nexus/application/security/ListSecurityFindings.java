package com.ivan.nexus.application.security;

import com.ivan.nexus.domain.security.SecurityFinding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListSecurityFindings {
    private final SecurityFindingStore findings;

    public ListSecurityFindings(SecurityFindingStore findings) {
        this.findings = findings;
    }

    @Transactional(readOnly = true)
    public List<SecurityFinding> execute(String projectId) {
        return findings.listByProject(projectId);
    }
}
