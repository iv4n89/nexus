package com.ivan.nexus.application.security;

import com.ivan.nexus.application.activity.RecordActivity;
import com.ivan.nexus.application.alert.AlertRuleStore;
import com.ivan.nexus.application.alert.AlertStore;
import com.ivan.nexus.domain.activity.ActivityType;
import com.ivan.nexus.domain.alert.Alert;
import com.ivan.nexus.domain.alert.AlertKey;
import com.ivan.nexus.domain.alert.AlertRule;
import com.ivan.nexus.domain.alert.AlertType;
import com.ivan.nexus.domain.security.SecurityFinding;
import com.ivan.nexus.domain.security.SecurityFindingFingerprint;
import com.ivan.nexus.domain.security.SecurityFindingStatus;
import com.ivan.nexus.domain.security.SecuritySeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RunSecurityScanTest {

    @Mock
    SecurityScanner scanner;
    @Mock
    SecurityFindingStore findings;
    @Mock
    RecordActivity recordActivity;
    @Mock
    AlertStore alerts;
    @Mock
    AlertRuleStore alertRules;

    @Test
    void scansFingerprintsStoresAndRecordsActivity() {
        RawSecurityFinding raw = new RawSecurityFinding(
                SecuritySeverity.MEDIUM,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.1",
                "CVE-2024-1");
        given(scanner.scan("lab")).willReturn(List.of(raw));

        String expectedFingerprint = SecurityFindingFingerprint.compute(
                "trivy", "openssl", "CVE-2024-1", "1.0.0");
        SecurityFinding stored = new SecurityFinding(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "lab",
                SecuritySeverity.MEDIUM,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.1",
                "CVE-2024-1",
                expectedFingerprint,
                SecurityFindingStatus.OPEN,
                Instant.parse("2026-09-18T00:00:00Z"),
                Instant.parse("2026-09-18T00:00:00Z"));
        given(findings.upsertByFingerprint(any())).willReturn(stored);

        List<SecurityFinding> result =
                new RunSecurityScan(scanner, findings, recordActivity, alerts, alertRules).execute("lab");

        assertThat(result).containsExactly(stored);

        ArgumentCaptor<SecurityFinding> captor = ArgumentCaptor.forClass(SecurityFinding.class);
        verify(findings).upsertByFingerprint(captor.capture());
        SecurityFinding candidate = captor.getValue();
        assertThat(candidate.projectId()).isEqualTo("lab");
        assertThat(candidate.fingerprint()).isEqualTo(expectedFingerprint);
        assertThat(candidate.status()).isEqualTo(SecurityFindingStatus.OPEN);

        verify(recordActivity).execute(
                eq(ActivityType.SECURITY_SCAN_COMPLETED),
                eq("lab"),
                eq(null),
                eq("security scan completed"),
                eq(Map.of("findingCount", 1)));
        verify(alerts, never()).open(any());
    }

    @Test
    void opensAlertForHighAndCriticalFindings() {
        RawSecurityFinding raw = new RawSecurityFinding(
                SecuritySeverity.HIGH,
                "npm-audit",
                "lodash",
                "<4.17.21",
                "4.17.21",
                "Prototype Pollution");
        given(scanner.scan("lab")).willReturn(List.of(raw));

        String fingerprint = SecurityFindingFingerprint.compute(
                "npm-audit", "lodash", "Prototype Pollution", "<4.17.21");
        SecurityFinding stored = new SecurityFinding(
                UUID.randomUUID(),
                "lab",
                SecuritySeverity.HIGH,
                "npm-audit",
                "lodash",
                "<4.17.21",
                "4.17.21",
                "Prototype Pollution",
                fingerprint,
                SecurityFindingStatus.OPEN,
                Instant.now(),
                Instant.now());
        given(findings.upsertByFingerprint(any())).willReturn(stored);
        given(alerts.findOpen(new AlertKey(AlertType.SECURITY_FINDING, "lab", fingerprint)))
                .willReturn(Optional.empty());
        UUID ruleId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        given(alertRules.findEnabled()).willReturn(List.of(
                new AlertRule(ruleId, null, AlertType.SECURITY_FINDING, Map.of(), true)));

        new RunSecurityScan(scanner, findings, recordActivity, alerts, alertRules).execute("lab");

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alerts).open(alertCaptor.capture());
        assertThat(alertCaptor.getValue().type()).isEqualTo(AlertType.SECURITY_FINDING);
        assertThat(alertCaptor.getValue().serviceId()).isEqualTo(fingerprint);
        assertThat(alertCaptor.getValue().ruleId()).isEqualTo(ruleId);
        verify(recordActivity).execute(
                eq(ActivityType.ALERT_CREATED),
                eq("lab"),
                eq(fingerprint),
                eq("alert created"),
                any());
    }

    @Test
    void emptyScanStillRecordsActivity() {
        given(scanner.scan("lab")).willReturn(List.of());

        List<SecurityFinding> result =
                new RunSecurityScan(scanner, findings, recordActivity, alerts, alertRules).execute("lab");

        assertThat(result).isEmpty();
        verify(recordActivity).execute(
                ActivityType.SECURITY_SCAN_COMPLETED,
                "lab",
                null,
                "security scan completed",
                Map.of("findingCount", 0));
    }
}
