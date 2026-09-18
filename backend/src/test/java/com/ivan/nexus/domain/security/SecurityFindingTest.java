package com.ivan.nexus.domain.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFindingTest {

    @Test
    void acknowledgeSetsStatus() {
        SecurityFinding finding = sample(SecurityFindingStatus.OPEN);
        SecurityFinding acknowledged = finding.acknowledge(Instant.parse("2026-09-18T10:00:00Z"));
        assertThat(acknowledged.status()).isEqualTo(SecurityFindingStatus.ACKNOWLEDGED);
        assertThat(acknowledged.id()).isEqualTo(finding.id());
    }

    @Test
    void seenAgainReopensResolvedAndUpdatesLastSeen() {
        Instant first = Instant.parse("2026-09-01T00:00:00Z");
        Instant again = Instant.parse("2026-09-18T00:00:00Z");
        SecurityFinding resolved = new SecurityFinding(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "lab",
                SecuritySeverity.HIGH,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.1",
                "CVE-1",
                "fp",
                SecurityFindingStatus.RESOLVED,
                first,
                first);

        SecurityFinding updated = resolved.seenAgain(
                SecuritySeverity.CRITICAL,
                "trivy",
                "openssl",
                "1.0.0",
                "1.0.2",
                "CVE-1",
                again);

        assertThat(updated.status()).isEqualTo(SecurityFindingStatus.OPEN);
        assertThat(updated.severity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(updated.fixedVersion()).isEqualTo("1.0.2");
        assertThat(updated.firstSeen()).isEqualTo(first);
        assertThat(updated.lastSeen()).isEqualTo(again);
    }

    private static SecurityFinding sample(SecurityFindingStatus status) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return new SecurityFinding(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "lab",
                SecuritySeverity.MEDIUM,
                "trivy",
                "lodash",
                "4.17.20",
                "4.17.21",
                "Prototype pollution",
                "fingerprint",
                status,
                now,
                now);
    }
}
