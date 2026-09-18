package com.ivan.nexus.application.security;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.alert.AlertStatus;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingFingerprint;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
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
    private final AlertStore alerts;
    private final AlertRuleStore alertRules;

    public RunSecurityScan(
            SecurityScanner scanner,
            SecurityFindingStore findings,
            RecordActivity recordActivity,
            AlertStore alerts,
            AlertRuleStore alertRules) {
        this.scanner = scanner;
        this.findings = findings;
        this.recordActivity = recordActivity;
        this.alerts = alerts;
        this.alertRules = alertRules;
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
            SecurityFinding persisted = findings.upsertByFingerprint(candidate);
            stored.add(persisted);
            maybeOpenAlert(persisted, now);
        }

        recordActivity.execute(
                ActivityType.SECURITY_SCAN_COMPLETED,
                projectId,
                null,
                "security scan completed",
                Map.of("findingCount", stored.size()));

        return List.copyOf(stored);
    }

    private void maybeOpenAlert(SecurityFinding finding, Instant now) {
        if (finding.severity() != SecuritySeverity.HIGH
                && finding.severity() != SecuritySeverity.CRITICAL) {
            return;
        }
        AlertKey key = new AlertKey(AlertType.SECURITY_FINDING, finding.projectId(), finding.fingerprint());
        if (alerts.findOpen(key).isPresent()) {
            return;
        }
        AlertRule rule = alertRules.findEnabled().stream()
                .filter(r -> r.type() == AlertType.SECURITY_FINDING)
                .filter(r -> r.projectId() == null || finding.projectId().equals(r.projectId()))
                .findFirst()
                .orElse(null);
        if (rule == null) {
            return;
        }
        String message = finding.severity() + " security finding: " + finding.title()
                + (finding.packageName() == null ? "" : " (" + finding.packageName() + ")");
        alerts.open(new Alert(
                UUID.randomUUID(),
                rule.id(),
                finding.projectId(),
                finding.fingerprint(),
                AlertStatus.ACTIVE,
                message,
                now,
                null,
                null,
                AlertType.SECURITY_FINDING));
        recordActivity.execute(
                ActivityType.ALERT_CREATED,
                finding.projectId(),
                finding.fingerprint(),
                "alert created",
                Map.of("alertType", AlertType.SECURITY_FINDING.name(), "detail", message));
    }
}
