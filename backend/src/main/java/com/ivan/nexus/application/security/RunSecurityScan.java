package com.ivan.nexus.application.security;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingFingerprint;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RunSecurityScan {
    private final SecurityScanner scanner;
    private final SecurityFindingStore findings;
    private final RecordActivity recordActivity;

    public RunSecurityScan(
            SecurityScanner scanner,
            SecurityFindingStore findings,
            RecordActivity recordActivity) {
        this.scanner = scanner;
        this.findings = findings;
        this.recordActivity = recordActivity;
    }

    @Transactional
    public List<SecurityFinding> execute(String projectId) {
        Instant now = Instant.now();
        List<RawSecurityFinding> rawFindings = scanner.scan(projectId);
        List<SecurityFinding> stored = new ArrayList<>(rawFindings.size());

        for (RawSecurityFinding raw : rawFindings) {
            String fingerprint = SecurityFindingFingerprint.compute(
                    raw.source(), raw.packageName(), raw.title(), raw.installedVersion());
            SecurityFinding candidate = new SecurityFinding(
                    UUID.randomUUID(),
                    projectId,
                    raw.severity(),
                    raw.source(),
                    raw.packageName(),
                    raw.installedVersion(),
                    raw.fixedVersion(),
                    raw.title(),
                    fingerprint,
                    SecurityFindingStatus.OPEN,
                    now,
                    now);
            stored.add(findings.upsertByFingerprint(candidate));
        }

        recordActivity.execute(
                ActivityType.SECURITY_SCAN_COMPLETED,
                projectId,
                null,
                "security scan completed",
                Map.of("findingCount", stored.size()));

        return List.copyOf(stored);
    }
}
